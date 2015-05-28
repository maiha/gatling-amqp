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
  var _destination: Option[AmqpDestination] = None,
  val requests: mutable.ArrayBuffer[AmqpRequest] = mutable.ArrayBuffer[AmqpRequest]()
) extends Publishable {

  def publishRequest: PublishRequest = requests.headOption match {
    case Some(req: PublishRequest) => req
    case _ => throw new RuntimeException("PublishRequest not found")
  }

  def build: Seq[AmqpRequest] = requests.toSeq
}

object AmqpRequestBuilder {
  def apply(requestName: Expression[String]): AmqpRequestBuilder = {
    new AmqpRequestBuilder(requestName)
  }
}
