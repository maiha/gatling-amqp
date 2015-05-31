package io.gatling.amqp.infra

import com.rabbitmq.client._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.amqp.util._
import io.gatling.core.util.TimeHelper.nowMillis
import io.gatling.core.session.Session
import java.util.concurrent.atomic._
import scala.util._
import pl.project13.scala.rainbow._

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
          sendEvent(AmqpPublishAcked (actorName, no.toInt, multi, nowMillis))
        }

        def handleNack(no: Long, multi: Boolean): Unit =
          sendEvent(AmqpPublishNacked(actorName, no.toInt, multi, nowMillis))
      })
    }
  }

  private val localPublishSeqNoCounter = new AtomicInteger(1)
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

  protected def publishSync(req: PublishRequest, session: Session): Unit = {
    import req._
    val startedAt = nowMillis
    val no: Int = getNextPublishSeqNo
    val event = AmqpPublishing(actorName, no, nowMillis, req, session)
    Try {
      channel.basicPublish(exchange.name, routingKey, props, bytes)
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
      channel.basicPublish(exchange.name, routingKey, props, bytes)
    } catch {
      case e: Exception =>
        sendEvent(AmqpPublishFailed(actorName, no, nowMillis, e))
        log.error(s"basicPublish($exchange) failed", e)
    }
  }
}
