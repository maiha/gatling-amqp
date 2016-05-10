package io.gatling.amqp.config

import akka.actor._
import com.rabbitmq.client.ConnectionFactory
import com.typesafe.scalalogging.StrictLogging
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.controller.throttle.Throttler
import io.gatling.core.protocol.Protocol
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

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
    // TODO support for warmup removed? perhaps onStart? //super.warmUp(system, statsEngine, throttler)
    setupVariables(system, statsEngine)
    awaitPreparation()
  }

  /**
   * finalize user session about AMQP (invoked by gatling framework)
   */
  def userEnd(session: Session): Unit = {
    awaitTerminationFor(session)
    // TODO support for end removed? perhaps onExit? //super.userEnd(session)
  }

  override def toString: String = {
    s"AmqpProtocol(hashCode=$hashCode)"
  }
}
