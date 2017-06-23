package io.gatling.amqp.action

import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.request.builder._
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.protocol.ProtocolComponentsRegistry
import io.gatling.core.util.NameGen
import io.gatling.core.structure.ScenarioContext

class AmqpActionBuilder(amqpRequestBuilder: AmqpRequestBuilder)(implicit amqp: AmqpProtocol) extends ActionBuilder with NameGen {
  private def components(protocolComponentsRegistry: ProtocolComponentsRegistry) = protocolComponentsRegistry.components(AmqpProtocol.AmqpProtocolKey)

  override def build(ctx: ScenarioContext, next: Action): Action = {
    import ctx._
    val statsEngine = coreComponents.statsEngine
    val amqpComponents = components(protocolComponentsRegistry)
    amqpRequestBuilder.build match {
      case req: PublishRequest =>
        new AmqpPublishAction(req, next)
      case req: ConsumeRequest =>
        new AmqpConsumeAction(req, next)
    }
  }
}