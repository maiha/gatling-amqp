package io.gatling.amqp.config

import com.typesafe.config._
import com.github.kxbmap.configs._
import io.gatling.amqp.data._

case class AmqpConfig(config: Config) {
  // configs
  private val amqp = config.getConfig("amqp")

  // variables
  private val host     = amqp.get[String]("host")
  private val port     = amqp.get[Int]("port")
  private val vhost    = amqp.get[String]("vhost")
  private val user     = amqp.get[String]("user")
  private val password = amqp.get[String]("password")
  private val poolSize = amqp.get[Int]("poolSize")

  def toAmqpProtocol: AmqpProtocol = {
    AmqpProtocol.default.copy(
      connection = Connection(host = host, port = port, vhost = vhost, user = user, password = password, poolSize = poolSize)
    )
  }
}
