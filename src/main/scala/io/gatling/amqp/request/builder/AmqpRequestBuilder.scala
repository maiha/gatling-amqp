package io.gatling.amqp.request.builder

import akka.actor._
import io.gatling.amqp.action._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.request._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.Expression
import io.gatling.core.structure.ScenarioContext

import scala.collection.mutable

// (implicit configuration: GatlingConfiguration)
class AmqpRequestBuilder(
  requestName: Expression[String],
  var _request: Option[AmqpRequest] = None
) extends Publishing with Consuming {

  def build: AmqpRequest = _request.getOrElse(throw new RuntimeException("No AmqpRequest Found"))
}

object AmqpRequestBuilder {
  def apply(requestName: Expression[String]): AmqpRequestBuilder = {
    new AmqpRequestBuilder(requestName)
  }
}
