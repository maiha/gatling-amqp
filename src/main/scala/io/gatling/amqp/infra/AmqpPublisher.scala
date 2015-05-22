package io.gatling.amqp.infra

import akka.actor._
import com.rabbitmq.client.Channel
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import pl.project13.scala.rainbow._
import resource.managed

import scala.util.{Failure, Success}

class AmqpPublisher(implicit amqp: AmqpProtocol) extends AmqpActor {
  override def receive = {
    case msg@ PublishRequest(ex, routingKey, props, payload) =>
      log.debug(s"PublishRequest(${ex.name}, $routingKey)")
      interact(msg) { _.basicPublish(ex.name, routingKey, props, payload) }
  }
}
