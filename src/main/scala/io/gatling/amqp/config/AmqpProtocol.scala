package io.gatling.amqp.config

import akka.actor._
import com.rabbitmq.client.ConnectionFactory
import com.typesafe.scalalogging.StrictLogging
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolKey}
import io.gatling.core.session.Session


object AmqpProtocol {

  lazy val AmqpProtocolKey = new ProtocolKey {

    type Protocol = AmqpProtocol
    type Components = AmqpComponents

    def protocolClass: Class[io.gatling.core.protocol.Protocol] = classOf[AmqpProtocol].asInstanceOf[Class[io.gatling.core.protocol.Protocol]]

    def defaultProtocolValue(configuration: GatlingConfiguration): AmqpProtocol = throw new IllegalStateException("Can't provide a default value for AmqpProtocol")

    def newComponents(system: ActorSystem, coreComponents: CoreComponents): AmqpProtocol => AmqpComponents = {
      amqpProtocol => {
        val amqpComponents = AmqpComponents(system, coreComponents, amqpProtocol)
        amqpProtocol.warmUp(system, coreComponents)
        amqpComponents
      }
    }
  }

  def apply(connection: Connection,
            preparings: List[AmqpChannelCommand]): AmqpProtocol =
    new AmqpProtocol(connection, preparings)
}

/**
 * Wraps a AMQP protocol configuration
 */
class AmqpProtocol(
  val connection: Connection,
  val preparings: List[AmqpChannelCommand]
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
   * warmUp AMQP protocol
   */
  def warmUp(system: ActorSystem, coreComponents: CoreComponents): Unit = {
    logger.info("amqp: warmUp start")
    setupVariables(system, coreComponents)
    awaitPreparation()
  }

  /**
   * finalize user session about AMQP
   */
  def userEnd(session: Session): Unit = {
    awaitTerminationFor(session)
  }

  override def toString: String = {
    s"AmqpProtocol(hashCode=$hashCode)"
  }
}
