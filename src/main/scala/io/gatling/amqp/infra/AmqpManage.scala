package io.gatling.amqp.infra

import java.io.IOException

import akka.actor.Props
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.core.result.writer.StatsEngine

import scala.collection.JavaConversions._
import scala.util.{Failure, Try}

class AmqpManage(statsEngine: StatsEngine)(implicit amqp: AmqpProtocol) extends AmqpActor {
  override def receive = {
    case msg@ DeclareExchange(AmqpExchange(name, tpe, durable, autoDelete, arguments)) =>
      log.info(s"Initializing AMQP exchange $name")
      interact(msg) { ch =>
        Try(ch.exchangeDeclare(name, tpe, durable, autoDelete, arguments)) match {
          case Failure(ex) =>
            ex match {
              case ex: IOException if Try(ex.getCause.getMessage).map(_.contains("reply-text=PRECONDITION_FAILED")).getOrElse(false) =>
                //channel error; protocol method: #method<channel.close>(reply-code=406, reply-text=PRECONDITION_FAILED - inequivalent arg 'auto_delete' for exchange 'luvar@local' in vhost '/': received 'true' but current is 'false', class-id=40, method-id=10)
                log.error("Declaring exchange, which is already there, but with different parameters! You can delete " +
                  "exchange and run simulation again, or change parameters of exchange in simulation to match existing " +
                  "exchange.", ex.getCause)
              case ex =>
                log.error("Unexpected failure!", ex)
            }
          case _ =>
        }
      }

    case msg@ DeclareQueue(AmqpQueue(name, durable, exclusive, autoDelete, arguments)) =>
      log.info(s"Initializing AMQP queue $name")
      interact(msg) { _.queueDeclare(name, durable, exclusive, autoDelete, arguments) }

    case msg@ BindQueue(exchange, queue, routingKey, arguments) =>
      log.info(s"Initializing AMQP binding $exchange to $queue")
      interact(msg) { _.queueBind(queue.name, exchange.name, routingKey, arguments) }
  }
}

object AmqpManage {
  def props(statsEngine : StatsEngine, amqp: AmqpProtocol) = Props(classOf[AmqpManage], statsEngine, amqp)
}