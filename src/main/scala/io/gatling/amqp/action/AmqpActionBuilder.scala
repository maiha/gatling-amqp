package io.gatling.amqp.action

import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.request.builder._
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.protocol.ProtocolComponentsRegistry
import io.gatling.core.structure.ScenarioContext
import io.gatling.http.action.sync.HttpRequestAction
import io.gatling.http.protocol.HttpComponents

class AmqpActionBuilder(amqpRequestBuilder: AmqpRequestBuilder)(implicit amqp: AmqpProtocol) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action = {
    // next line will cause to call warmup method, which will cause call of setup method for roters and so on.
    val amqpComponents = lookUpAmqpComponents(ctx.protocolComponentsRegistry)
    val amqpRequest = amqpRequestBuilder.build//(ctx.coreComponents, amqpComponents, ctx.throttled)

    amqpRequest match {
      case req: PublishRequest =>
        new AmqpPublishAction(req, next)(amqp)
      case req: ConsumeRequest =>
        // TODO check if here should be consume action, or (like before migrating to new gatling dependency) publish action
        new AmqpConsumeAction(req, next)(amqp)
    }
  }

  def lookUpAmqpComponents(protocolComponentsRegistry: ProtocolComponentsRegistry): AmqpComponents =
    protocolComponentsRegistry.components(AmqpProtocol.AmqpProtocolKey)
}
