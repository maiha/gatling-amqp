package io.gatling.amqp.config

import akka.actor._
import com.rabbitmq.client.ConnectionFactory
import com.typesafe.scalalogging.StrictLogging

import io.gatling.core.config.Protocol
import io.gatling.core.controller.throttle.Throttler
import io.gatling.core.result.writer.StatsEngine
import io.gatling.core.session.Session
import io.gatling.amqp.data._
import io.gatling.amqp.infra._

import pl.project13.scala.rainbow._

/**
 * Wraps a AMQP protocol configuration
 */
case class AmqpProtocol(
  connection: Connection = Connection.default
) extends Protocol with AmqpVariables with AmqpPreparation with StrictLogging {

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
    connection.validate
  }

  /**
   * warmUp AMQP protocol (invoked by gatling framework)
   */
  override def warmUp(system: ActorSystem, statsEngine: StatsEngine, throttler: Throttler): Unit = {
    super.warmUp(system, statsEngine, throttler)
    setupVariables(system)
    awaitPreparation()
  }

  /**
   * finalize user session about AMQP (invoked by gatling framework)
   */
  override def userEnd(session: Session): Unit = {
    super.userEnd(session)
  }

  override def toString: String = {
    s"AmqpProtocol(hashCode=$hashCode)"
  }
}

object AmqpProtocol {
  def default: AmqpProtocol = new AmqpProtocol
}
