package io.gatling.amqp.infra

import akka.actor._
import com.rabbitmq.client.Channel
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import pl.project13.scala.rainbow._
import resource.managed
import scala.util.{Failure, Success}
import io.gatling.amqp.infra.AmqpActor.ConnectionClosed

abstract class AmqpActor(implicit amqp: AmqpProtocol) extends Actor with ActorLogging {
  protected lazy val className = getClass().getSimpleName
  protected lazy val conn = amqp.newConnection
  protected var _channel: Option[Channel] = None

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"amqp: Start actor `$className'".yellow)
    open()
  }

  override def postStop(): Unit = {
    close()
    log.info(s"amqp: Stop actor `$className'".yellow)
    super.preStart()
  }

  protected def open(): Unit = _channel match {
    case Some(_) => // nop
    case None => _channel = Some(conn.createChannel())
  }

  protected def close(): Unit = {
    _channel.foreach(_.close())
    _channel = None
  }

  protected def channel: Channel = {
    open()
    _channel.getOrElse( throw new ConnectionClosed )
  }

  protected def isOpened: Boolean = _channel.isDefined

  protected def onChannel[A](action: Channel => A) = {
    if (isOpened) {
      action(channel)
    } else {
      for (channel <- managed(conn.createChannel())) {
        action(channel)
      }
    }
  }

  protected def interact[A](successMsg: Any)(action: Channel => A) = {
    onChannel { channel =>
      try {
        action(channel)
        sender() ! Success(successMsg)
      } catch {
        case e: Throwable => sender() ! Failure(e)
      }
    }
  }
}

object AmqpActor {
  class ConnectionClosed extends RuntimeException
}
