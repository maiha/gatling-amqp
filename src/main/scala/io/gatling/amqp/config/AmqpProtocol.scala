package io.gatling.amqp.config

import akka.actor.ActorSystem
import com.rabbitmq.client.ConnectionFactory
import com.typesafe.scalalogging.StrictLogging
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.controller.throttle.Throttler
import io.gatling.core.protocol.{Protocol, ProtocolKey}
import io.gatling.core.stats.StatsEngine

object AmqpProtocol {
  val AmqpProtocolKey = new ProtocolKey {

    type Protocol = AmqpProtocol
    type Components = AmqpComponents
    def protocolClass: Class[io.gatling.core.protocol.Protocol] = classOf[AmqpProtocol].asInstanceOf[Class[io.gatling.core.protocol.Protocol]]

    def defaultValue(configuration: GatlingConfiguration): AmqpProtocol = AmqpProtocol(configuration)

    def newComponents(system: ActorSystem, coreComponents: CoreComponents): AmqpProtocol => AmqpComponents = {
      amqpProtocol => {
        val amqpComponents = AmqpComponents(amqpProtocol)
        amqpProtocol.warmUp(system, coreComponents.statsEngine, coreComponents.throttler)
        amqpComponents
      }
    }
  }

  def apply(conf: GatlingConfiguration, connection: Connection, preparings: List[AmqpChannelCommand]): AmqpProtocol = new AmqpProtocol(connection, preparings)

  def apply(conf: GatlingConfiguration): AmqpProtocol = new AmqpProtocol(connection = null, preparings = null)
}

/**
 * Wraps a AMQP protocol configuration
 */
case class AmqpProtocol(
  connection: Connection,
  preparings: List[AmqpChannelCommand]
) extends Protocol with AmqpVariables with AmqpPreparation with AmqpTermination with AmqpRunner with StrictLogging {
  lazy val event: AmqpEventBus = new AmqpEventBus()  // not used yet cause messages seems in random order in the bus

  /**
   * create new AMQP connection
   */
  def newConnection: com.rabbitmq.client.Connection = {
    import connection._
    val factory = new ConnectionFactory()
    factory.setHost(host)
    factory.setPort(port)
    factory.setUsername(user)
    factory.setPassword(password)
    factory.setVirtualHost(vhost)
    factory.newConnection
  }

  /**
   * validate variables
   */
  def validate(): Unit = {
    connection.validate()
  }

  /**
   * Whether is AMQP channel used for confirmation mode? (RabbitMQ feature)
   */
  def isConfirmMode: Boolean = connection.confirm

  /**
   * warmUp AMQP protocol (invoked by gatling framework)
   */
  def warmUp(system: ActorSystem, statsEngine: StatsEngine, throttler: Throttler): Unit = {
    logger.info("amqp: warmUp start")
    setupVariables(system, statsEngine)
    awaitPreparation()
  }

  override def toString: String = {
    s"AmqpProtocol(hashCode=$hashCode)"
  }
}
