package io.gatling.amqp.infra

import akka.actor._
import com.rabbitmq.client.{AlreadyClosedException, Channel}
import io.gatling.amqp.config._
import io.gatling.amqp.infra.AmqpActor.ConnectionClosed
import io.gatling.core.akka.BaseActor
import pl.project13.scala.rainbow._

import scala.util.{Failure, Success}

abstract class AmqpActor(implicit amqp: AmqpProtocol) extends BaseActor {
  protected lazy val conn = amqp.newConnection
  protected var _channel: Option[Channel] = None
  protected lazy val className = getClass.getSimpleName
  protected val log = logger // gap between LazyLogging and ActorLogging
  protected def stopMessage: String = ""

  override def receive: Receive = {
    ???
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    logger.error(s"Actor $this crashed on message $message".red, reason)
    sys.exit(-1)
  }

  override def preStart(): Unit = {
    super.preStart()
    open()
  }

  override def postStop(): Unit = {
    close()
    super.postStop()
  }

  protected def open(): Unit = _channel match {
    case Some(_) => // nop
    case None => _channel = Some(conn.createChannel())
  }

  protected def close(): Unit = {
    try {
      _channel.foreach(_.close())
    } catch {
      case e: AlreadyClosedException =>
        // already handled
        // logger.debug(s"Connection.close failed: $e".yellow, e)
    }
    _channel = None
    conn.close()
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
      val channel = conn.createChannel()
      try {
        action(channel)
      } finally {
        channel.close()
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
