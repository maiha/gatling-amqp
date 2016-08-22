package io.gatling.amqp.infra

import java.io.IOException

import akka.actor.Props
import com.typesafe.scalalogging.Logger
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.infra.AmqpManage.handleExceptions
import io.gatling.core.stats.StatsEngine

import scala.collection.JavaConversions._
import scala.util.{Failure, Try}

class AmqpManage(statsEngine: StatsEngine)(implicit amqp: AmqpProtocol) extends AmqpActor {

  override def receive = {
    case msg@ DeclareExchange(AmqpExchange(name, tpe, durable, autoDelete, arguments)) =>
      log.info(s"Initializing AMQP exchange $name")
      interact(msg) { ch =>
        handleExceptions(log, "exchange initialization", Try(ch.exchangeDeclare(name, tpe, durable, autoDelete, arguments)))
        }

    case msg@ DeclareQueue(AmqpQueue(name, durable, exclusive, autoDelete, arguments)) =>
      log.info(s"Initializing AMQP queue $name")
      interact(msg) { ch =>
        handleExceptions(log, "queue initialization", Try(ch.queueDeclare(name, durable, exclusive, autoDelete, arguments)))
      }

    case msg@ BindQueue(exchange, queue, routingKey, arguments) =>
      log.info(s"Initializing AMQP binding $exchange to $queue")
      interact(msg) { ch =>
        handleExceptions(log, "binding initialization", Try(ch.queueBind(queue.name, exchange.name, routingKey, arguments)))
      }
  }
}

object AmqpManage {
  def props(statsEngine: StatsEngine, amqp: AmqpProtocol) = Props(classOf[AmqpManage], statsEngine, amqp)

  def handleExceptions(log: Logger, operation: String, triedOk: Try[AnyRef]): Unit = {
    triedOk match {
      case Failure(ex) =>
        ex match {
          case ex: IOException if Try(ex.getCause.getMessage).map(msg =>
            msg.contains("reply-text=PRECONDITION_FAILED") &&
              msg.contains("but current is")
          ).getOrElse(false) =>
            //channel error; protocol method: #method<channel.close>(reply-code=406, reply-text=PRECONDITION_FAILED - inequivalent arg 'auto_delete' for exchange 'luvar@local' in vhost '/': received 'true' but current is 'false', class-id=40, method-id=10)
            log.error("Executing " + operation + " failed. Operating on item which is already there, but with different parameters! You can delete " +
              "given item and run simulation again, or change parameters of item in simulation to match existing one. Going to rethrow ex.", ex.getCause)
          case ex =>
            log.error("Unexpected failure during execution of " + operation + "! Going to rethrow ex.", ex)
        }
        throw ex
      case _ =>
    }
  }
}