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
        system.actorOf(AmqpPublishAction.props(req, next, amqp), actorName("AmqpPublishAction"))
      case req: ConsumeRequest =>
        req match {
          case ConsumeSingleMessageRequest(_, _, _, _, Some(_)) =>
            system.actorOf(AmqpConsumeCorrelatedAction.props(req, next, amqp), actorName("AmqpConsumeCorrelatedAction"))
          case _ =>
            // router will create single actor for this scenario step, for each user/session
            system.actorOf(AmqpConsumeAction.props(req, next, amqp), actorName("AmqpConsumeAction"))
        }
    }
  }
}
