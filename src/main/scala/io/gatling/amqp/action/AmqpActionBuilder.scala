package io.gatling.amqp.action

import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.request.builder._
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.Expression
import io.gatling.core.structure.ScenarioContext

class AmqpActionBuilder(amqpRequestBuilder: AmqpRequestBuilder)
  extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    import ctx._
    val amqpComponents = protocolComponentsRegistry.components(AmqpProtocol.AmqpProtocolKey)
    val statsEngine = coreComponents.statsEngine
    val req: Expression[_ <: AmqpRequest] = amqpRequestBuilder.build
    implicit val amqpProtocol = amqpComponents.amqpProtocol
    amqpRequestBuilder.publishRequestType match {
      case Some(true) =>
        AmqpPublishAction(AmqpAttributes(amqpRequestBuilder.requestName, req.asInstanceOf[Expression[PublishRequest]]), statsEngine, next)
      case Some(false) =>
        AmqpConsumeAction(AmqpAttributes(amqpRequestBuilder.requestName, req.asInstanceOf[Expression[ConsumeRequest]]), statsEngine, next)
      case None =>
        throw new RuntimeException("No AmqpRequest Found. A possible issue could be improper definition of scenario.")
    }
  }
}
