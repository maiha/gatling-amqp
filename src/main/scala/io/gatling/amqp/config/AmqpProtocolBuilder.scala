package io.gatling.amqp.config

/**
 * Builder for AmqpProtocol used in DSL
 *
 * @param protocol the protocol being built
 *
 * TODO: use Lens, or make it mutable
 */
case class AmqpProtocolBuilder(protocol: AmqpProtocol) {
  // primary accessors
  def host(h: String)     = copy(protocol = protocol.copy(connection = protocol.connection.copy(host = h)))
  def port(p: Int)        = copy(protocol = protocol.copy(connection = protocol.connection.copy(port = p)))
  def vhost(v: String)    = copy(protocol = protocol.copy(connection = protocol.connection.copy(vhost = v)))
  def poolSize(p: Int)    = copy(protocol = protocol.copy(connection = protocol.connection.copy(poolSize = p)))
  def user(u: String)     = copy(protocol = protocol.copy(connection = protocol.connection.copy(user = u)))
  def password(p: String) = copy(protocol = protocol.copy(connection = protocol.connection.copy(password = p)))
  def confirm(b: Boolean) = copy(protocol = protocol.copy(connection = protocol.connection.copy(confirm = b)))

  // shortcuts
  def auth(u: String, p: String) = user(u).password(p)
  def confirmMode()       = confirm(true)

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


