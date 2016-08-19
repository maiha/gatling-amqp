package io.gatling.amqp.request.builder

import io.gatling.amqp.data._
import io.gatling.core.session.Expression

// TODO: use (implicit configuration: GatlingConfiguration)
class AmqpRequestBuilder(
                          val requestName: Expression[String],
                          var _request: Option[AmqpRequest] = None
) extends Publishing with Consuming {

  def build: AmqpRequest = _request.getOrElse(throw new RuntimeException("No AmqpRequest Found"))
}

object AmqpRequestBuilder {
  def apply(requestName: Expression[String]): AmqpRequestBuilder = {
    new AmqpRequestBuilder(requestName)
  }
}
