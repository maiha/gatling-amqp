package io.gatling.amqp.config

import akka.actor._
import akka.event.EventBus
import akka.event.LookupClassification
import com.rabbitmq.client.ConnectionFactory
import com.typesafe.scalalogging.StrictLogging
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.config.Protocol
import io.gatling.core.controller.throttle.Throttler
import io.gatling.core.result.writer.StatsEngine
import io.gatling.core.session.Session
import pl.project13.scala.rainbow._

/**
 * Wraps a AMQP protocol configuration
 */
case class AmqpProtocol(
  connection: Connection,
  preparings: List[AmqpChannelCommand]
) extends Protocol with AmqpVariables with AmqpPreparation with StrictLogging {
  val event: AmqpEventBus = new AmqpEventBus()

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
  override def warmUp(system: ActorSystem, statsEngine: StatsEngine, throttler: Throttler): Unit = {
    logger.info("amqp: warmUp start")
    super.warmUp(system, statsEngine, throttler)
    setupVariables(system, statsEngine)
    awaitPreparation()
  }

  /**
   * finalize user session about AMQP (invoked by gatling framework)
   */
  override def userEnd(session: Session): Unit = {
    awaitTerminationFor(session)
    super.userEnd(session)
  }

  override def toString: String = {
    s"AmqpProtocol(hashCode=$hashCode)"
  }
}
