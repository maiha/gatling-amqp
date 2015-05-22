package io.gatling.amqp.request.builder

import akka.actor._
import io.gatling.amqp.action._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.Expression
import io.gatling.core.structure.ScenarioContext

import scala.collection.mutable.ArrayBuffer

// (implicit configuration: GatlingConfiguration)
class AmqpRequestBuilder(
  requestName: Expression[String],
  val requests: ArrayBuffer[AmqpRequest] = ArrayBuffer[AmqpRequest]()
) extends Publishable {
  def build: Seq[AmqpRequest] = requests.toSeq
}

/*
class AmqpRequestActionBuilder(requestBuilder: AmqpRequestBuilder, amqpEngine: AmqpEngine) extends AmqpActionBuilder {
  def build(system: ActorSystem, next: ActorRef, ctx: ScenarioContext): ActorRef = {
    val amqpRequest = requestBuilder.build(ctx.protocols.protocol[AmqpProtocol], ctx.throttled)
    system.actorOf(AmqpRequestAction.props(amqpRequest, amqpEngine, ctx.statsEngine, next), actorName("amqpRequest"))
  }
}
 */

class PublishActionBuilder(request: PublishRequest)(implicit amqp: AmqpProtocol) extends ActionBuilder {
  def build(system: ActorSystem, next: ActorRef, ctx: ScenarioContext): ActorRef = {
    system.actorOf(Props(new PublishAction(next, ctx, request)))
  }
}

object AmqpRequestBuilder {
  implicit def toActionBuilder(amqpRequestBuilder: AmqpRequestBuilder)(implicit amqp: AmqpProtocol): ActionBuilder = {
    amqpRequestBuilder.build.head match {
      case req: PublishRequest => new PublishActionBuilder(req)
      case _ => throw new RuntimeException("not implemented yet")
    }
  }

  def apply(requestName: Expression[String]): AmqpRequestBuilder = {
    new AmqpRequestBuilder(requestName)
  }
}
