package io.gatling.amqp.infra

import akka.actor._
import com.rabbitmq.client.Channel
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import resource.managed

import collection.JavaConversions._

class AmqpManager(implicit amqp: AmqpProtocol) extends AmqpActor {
  override def receive = {
    case msg@ DeclareExchange(name, tpe, durable, autoDelete, arguments) =>
      log.info(s"Initializing AMQP exchange $name")
      interact(msg) { _.exchangeDeclare(name, tpe, durable, autoDelete, arguments) }

    case msg@ DeclareQueue(name, durable, exclusive, autoDelete, arguments) =>
      log.info(s"Initializing AMQP queue $name")
      interact(msg) { _.queueDeclare(name, durable, exclusive, autoDelete, arguments) }

    case msg@ BindQueue(exchange, queue, routingKey, arguments) =>
      log.info(s"Initializing AMQP binding $exchange to $queue")
      interact(msg) { _.queueBind(queue, exchange, routingKey, arguments) }
  }
}
