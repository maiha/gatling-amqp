package io.gatling.amqp.config

import io.gatling.amqp.data._

/**
 * Builder for AmqpProtocol used in DSL
 *
 * @param protocol the protocol being built
 *
 * TODO: use Lens, or make it mutable
 */
case class AmqpProtocolBuilder(
  connection: Connection = Connection(),
  preparings: List[AmqpChannelCommand] = List[AmqpChannelCommand]()
) {
  // primary accessors
  def host(h: String)     = copy(connection = connection.copy(host = h))
  def port(p: Int)        = copy(connection = connection.copy(port = p))
  def vhost(v: String)    = copy(connection = connection.copy(vhost = v))

  /**
    * Number of publishers which will be used to publish messages. Round robin algorithm is used to publish messages
    * through pool of publishers.
    *
    * @param p number of publishers to pre-create
    * @return
    */
  def poolSize(p: Int)    = copy(connection = connection.copy(poolSize = p))
  def user(u: String)     = copy(connection = connection.copy(user = u))
  def password(p: String) = copy(connection = connection.copy(password = p))
  def confirm(b: Boolean) = copy(connection = connection.copy(confirm = b))

  // shortcuts
  def auth(u: String, p: String) = user(u).password(p)
  def confirmMode()       = confirm(true)

  // prepare
  def prepare(msg: AmqpChannelCommand) = copy(preparings = preparings :+ msg)
  def declare(q: AmqpQueue)   : AmqpProtocolBuilder = prepare(DeclareQueue(q))
  def declare(x: AmqpExchange): AmqpProtocolBuilder = prepare(DeclareExchange(x))
  def bind(x: AmqpExchange, q: AmqpQueue, routingKey: String = "", arguments: Arguments = DefaultArguments): AmqpProtocolBuilder =
    prepare(BindQueue(x, q, routingKey, arguments))

  def build: AmqpProtocol = {
    connection.validate
    AmqpProtocol(connection, preparings)
  }
}

/**
 * AmqpProtocolBuilder class companion
 */
object AmqpProtocolBuilder {
  implicit def toAmqpProtocol(builder: AmqpProtocolBuilder): AmqpProtocol = builder.build
}


