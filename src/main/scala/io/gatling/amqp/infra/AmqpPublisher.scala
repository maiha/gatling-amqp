package io.gatling.amqp.infra

import java.util.concurrent.atomic._

import akka.actor.Props
import com.rabbitmq.client._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.util.TimeHelper.nowMillis

import scala.util._

// next step is called asynchronously from AmqpPublishAction, so this actor cannot change session.
class AmqpPublisher(actorName: String)(implicit amqp: AmqpProtocol) extends AmqpActor {
  private val nacker = amqp.nacker
  private val isConfirmMode = amqp.isConfirmMode
  private def sendEvent(event: AmqpEvent): Unit = nacker ! event
//  private def sendEvent(event: AmqpEvent): Unit = amqp.event.publish(event)

  override def preStart(): Unit = {
    super.preStart()
    if (isConfirmMode) {
      channel.confirmSelect()

      channel.addConfirmListener(new ConfirmListener() {
        def handleAck (no: Long, multi: Boolean): Unit = {
          sendEvent(AmqpPublishAcked(actorName, no.toInt, multi, nowMillis))
        }

        def handleNack(no: Long, multi: Boolean): Unit =
          sendEvent(AmqpPublishNacked(actorName, no.toInt, multi, nowMillis))
      })
    }
  }

  private lazy val localPublishSeqNoCounter = new AtomicInteger(1)
  private def getNextPublishSeqNo: Int = {
    if (isConfirmMode)
      channel.getNextPublishSeqNo.toInt
    else
      localPublishSeqNoCounter.getAndIncrement
  }

  override def receive = {
    case AmqpPublishRequest(req, session) if isConfirmMode =>
      publishAsync(req, session)

    case AmqpPublishRequest(req, session) =>
      publishSync(req, session)
  }

  def getData(session: Session, bytes: scala.Either[Expression[Array[Byte]], Array[Byte]]): Array[Byte] = {
    bytes match {
      case Left(l) => l.apply(session).get
      case Right(r) => r
    }
  }

  protected def publishSync(req: PublishRequest, session: Session): Unit = {
    import req._
    val no: Int = getNextPublishSeqNo
    val event = AmqpPublishing(actorName, no, nowMillis, req, session)
    Try {
      val data: Array[Byte] = getData(session, bytes)
      channel.basicPublish(exchange.name, routingKey, props, data)
    } match {
      case Success(_) =>
        sendEvent(AmqpPublished(actorName, no, nowMillis, event))
      case Failure(e) =>
        sendEvent(AmqpPublishFailed(actorName, no, nowMillis, e))
        log.error(s"basicPublish($exchange) failed", e)
    }
  }

  protected def publishAsync(req: PublishRequest, session: Session): Unit = {
    import req._
    val no: Int = getNextPublishSeqNo
    sendEvent(AmqpPublishing(actorName, no, nowMillis, req, session))
    try {
      val data: Array[Byte] = getData(session, bytes)
      channel.basicPublish(exchange.name, routingKey, props, data)
    } catch {
      case e: Exception =>
        sendEvent(AmqpPublishFailed(actorName, no, nowMillis, e))
        log.error(s"basicPublish($exchange) failed", e)
    }
  }
}

object AmqpPublisher {
  def props(name: String, amqp: AmqpProtocol) = Props(classOf[AmqpPublisher], name, amqp)
}