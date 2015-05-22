package io.gatling.amqp.config

import io.gatling.core.filter.{ BlackList, Filters, WhiteList }
import io.gatling.core.session._
import io.gatling.core.session.el.El
import io.gatling.amqp._

/**
 * Builder for AmqpProtocol used in DSL
 *
 * @param protocol the protocol being built
 */
case class AmqpProtocolBuilder(protocol: AmqpProtocol) {
  def host(h: String)     = copy(protocol = protocol.copy(connection = protocol.connection.copy(host = h)))
  def port(p: Int)        = copy(protocol = protocol.copy(connection = protocol.connection.copy(port = p)))
  def vhost(v: String)    = copy(protocol = protocol.copy(connection = protocol.connection.copy(vhost = v)))
  def poolSize(p: Int)    = copy(protocol = protocol.copy(connection = protocol.connection.copy(poolSize = p)))
  def user(u: String)     = copy(protocol = protocol.copy(connection = protocol.connection.copy(user = u)))
  def password(p: String) = copy(protocol = protocol.copy(connection = protocol.connection.copy(password = p)))

  def auth(u: String, p: String) = user(u).password(p)

  def build: AmqpProtocol = {
    protocol.validate
    protocol
  }
}

/**
 * AmqpProtocolBuilder class companion
 */
object AmqpProtocolBuilder {
  implicit def toAmqpProtocol(builder: AmqpProtocolBuilder): AmqpProtocol = builder.build

  def default: AmqpProtocolBuilder = new AmqpProtocolBuilder(AmqpProtocol.default)
}


