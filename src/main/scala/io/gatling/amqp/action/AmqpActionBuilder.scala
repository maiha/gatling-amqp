package io.gatling.amqp.action

import akka.actor._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.request.builder._
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext

class AmqpActionBuilder(amqpRequestBuilder: AmqpRequestBuilder)(implicit amqp: AmqpProtocol) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action = {
    amqpRequestBuilder.build match {
      case req: PublishRequest =>
        //system.actorOf(AmqpPublishAction.props(req, next), "AmqpPublishAction")
        new AmqpPublishAction(req, next)
      case req: ConsumeRequest =>
        //system.actorOf(AmqpPublishAction.props(req, next), "AmqpConsumeAction")
        new AmqpPublishAction(req, next)
    }
  }
}
