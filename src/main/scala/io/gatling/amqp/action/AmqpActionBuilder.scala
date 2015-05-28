package io.gatling.amqp.action

import akka.actor._
import io.gatling.amqp.config._
import io.gatling.amqp.request.builder._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext

class AmqpActionBuilder(amqpRequestBuilder: AmqpRequestBuilder)(implicit amqp: AmqpProtocol) extends ActionBuilder {
  def build(system: ActorSystem, next: ActorRef, ctx: ScenarioContext): ActorRef = {
    val statsEngine = ctx.statsEngine
    val tracker = system.actorOf(AmqpRequestTrackerActor.props(statsEngine), actorName("amqpRequestTracker"))
    val req = amqpRequestBuilder.publishRequest
    system.actorOf(Props(new AmqpPublishAction(req, tracker, statsEngine, next)), actorName("amqpPublishAction"))
  }
}
