package io.gatling.amqp.infra

import com.rabbitmq.client._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.amqp.util._
import io.gatling.core.util.TimeHelper.nowMillis
import java.util.concurrent.atomic._
import pl.project13.scala.rainbow._

class AmqpPublisher(actorName: String)(implicit amqp: AmqpProtocol) extends AmqpActor {
  private val nacker = amqp.nacker
  private def sendEvent(event: AmqpEvent): Unit = nacker ! event
//  private def sendEvent(event: AmqpEvent): Unit = amqp.event.publish(event)

  override def preStart(): Unit = {
    super.preStart()
    if (amqp.isConfirmMode) {
      channel.confirmSelect()

      channel.addConfirmListener(new ConfirmListener() {
        def handleAck (no: Long, multi: Boolean): Unit =
          sendEvent(AmqpPublishAcked (actorName, no.toInt, multi, nowMillis))

        def handleNack(no: Long, multi: Boolean): Unit =
          sendEvent(AmqpPublishNacked(actorName, no.toInt, multi, nowMillis))
      })
    }
  }

  private val localPublishSeqNoCounter = new AtomicInteger(1)
  private def getNextPublishSeqNo: Int = {
    if (amqp.isConfirmMode)
      channel.getNextPublishSeqNo.toInt
    else
      localPublishSeqNoCounter.getAndIncrement
  }

  override def receive = {
    case AmqpPublishRequest(req, session) if amqp.isConfirmMode =>
      import req._
      val no: Int = getNextPublishSeqNo
      sendEvent(AmqpPublishing(actorName, no, req, nowMillis, session))
      try {
        channel.basicPublish(exchange.name, routingKey, props, bytes)
      } catch {
        case e: Exception =>
          sendEvent(AmqpPublishFailed(actorName, no, nowMillis, e))
          log.error(s"basicPublish($exchange) failed", e)
      }

    case AmqpPublishRequest(req, session) =>
      import req._
      val no: Int = getNextPublishSeqNo
      sendEvent(AmqpPublishing(actorName, no, req, nowMillis, session))
      try {
        channel.basicPublish(exchange.name, routingKey, props, bytes)
        sendEvent(AmqpPublished(actorName, no, nowMillis))
      } catch {
        case e: Exception =>
          sendEvent(AmqpPublishFailed(actorName, no, nowMillis, e))
          log.error(s"basicPublish($exchange) failed", e)
      }
  }
}
