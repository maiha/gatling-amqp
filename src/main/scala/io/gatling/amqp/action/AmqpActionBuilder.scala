package io.gatling.amqp.action

import akka.actor._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.request.builder._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext

class AmqpActionBuilder(amqpRequestBuilder: AmqpRequestBuilder)(implicit amqp: AmqpProtocol) extends ActionBuilder {
  def build(system: ActorSystem, next: ActorRef, ctx: ScenarioContext): ActorRef = {
    amqpRequestBuilder.build match {
      case req: PublishRequest =>
        system.actorOf(Props(new AmqpPublishAction(req, next)), actorName("AmqpPublishAction"))
      case req: ConsumeRequest =>
        system.actorOf(Props(new AmqpConsumeAction(req, next)), actorName("AmqpConsumeAction"))
    }
  }
}
