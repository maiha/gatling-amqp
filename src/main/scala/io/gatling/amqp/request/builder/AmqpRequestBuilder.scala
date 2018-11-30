package io.gatling.amqp.request.builder

import io.gatling.amqp.data._
import io.gatling.core.session.Expression

import scala.reflect.ClassTag

// TODO: use (implicit configuration: GatlingConfiguration)
class AmqpRequestBuilder(
  val requestName: Expression[String],
  var _request: Option[Expression[_ <: AmqpRequest]] = None,
  var publishRequestType: Option[Boolean] = None // TODO Use reflexion to get request type
) extends Publishing with Consuming {

  def build[T >: AmqpRequest : ClassTag]: Expression[T] = _request.
    getOrElse(throw new RuntimeException("No AmqpRequest Found. A possible issue could be improper definition of scenario."))
}

object AmqpRequestBuilder {
  def apply(requestName: Expression[String]): AmqpRequestBuilder = {
    new AmqpRequestBuilder(requestName)
  }
}
