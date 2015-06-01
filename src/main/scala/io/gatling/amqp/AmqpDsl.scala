package io.gatling.amqp

import io.gatling.amqp.action._
import io.gatling.amqp.check.AmqpCheckSupport
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.request.builder._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.Expression

trait AmqpDsl extends AmqpCheckSupport {

  def amqp = AmqpProtocolBuilder()

  /**
   * DSL text to start the amqp builder
   *
   * @param requestName human readable name of request
   * @return a PingBuilder instance which can be used to build up a ping
   */
  def amqp(requestName: Expression[String]) = AmqpRequestBuilder(requestName)

  /**
   * Convert a AmqpProtocolBuilder to a AmqpProtocol
   * <p>
   * Simplifies the API somewhat (you can pass the builder reference to the scenario .protocolConfig() method)
   */
  implicit def amqpProtocolBuilder2amqpProtocol(builder: AmqpProtocolBuilder): AmqpProtocol = builder.build

  implicit def amqpRequestBuilder2ActionBuilder(builder: AmqpRequestBuilder)(implicit amqp: AmqpProtocol): ActionBuilder = new AmqpActionBuilder(builder)

  val exchange = AmqpExchange
  val queue    = AmqpQueue
}
