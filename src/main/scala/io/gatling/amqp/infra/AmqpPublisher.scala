package io.gatling.amqp.infra

import io.gatling.amqp.config._
import io.gatling.amqp.data._

class AmqpPublisher(implicit amqp: AmqpProtocol) extends AmqpActor {
  override def receive = {
    case msg@ PublishRequest(ex, routingKey, props, payload) =>
      log.debug(s"PublishRequest(${ex.name}, $routingKey)")
      interact(msg) { _.basicPublish(ex.name, routingKey, props, payload) }
  }
}
