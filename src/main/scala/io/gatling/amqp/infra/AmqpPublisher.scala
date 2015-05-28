package io.gatling.amqp.infra

import com.rabbitmq.client._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.util._
import io.gatling.core.result.writer.StatsEngine
import pl.project13.scala.rainbow._

class AmqpPublisher(actorName: String, statsEngine: StatsEngine)(implicit amqp: AmqpProtocol) extends AmqpActor {
  class EventedQueue(override val logName: String) extends FastBitRequest[AmqpPublishEvent] {
    type Event = AmqpPublishEvent

    def accept(n: Int, multiple: Boolean): Unit = complete(n, multiple, (i, v) => v.onSuccess(i))
    def accept(n: Int): Unit = accept(n, multiple = false)

    def reject(n: Int, multiple: Boolean, e: Throwable): Unit = complete(n, multiple, (i, v) => v.onFailure(i, e))
    def reject(n: Int, e: Throwable): Unit = reject(n, multiple = false, e)

    override def request(n: Int, event: Event): Unit = synchronized {
      event.onProcess(n)
      super.request(n, event)
    }
  }

  private val eventQueue = new EventedQueue(actorName)
  private object Nacked extends RuntimeException

  override def preStart(): Unit = {
    super.preStart()
    if (amqp.isConfirmMode) {
      channel.addConfirmListener(new ConfirmListener() {
        def handleAck (no: Long, multiple: Boolean): Unit = self ! PublishAcked (no.toInt, multiple)
        def handleNack(no: Long, multiple: Boolean): Unit = self ! PublishNacked(no.toInt, multiple)
      })

      channel.confirmSelect()
    }
  }

  override def postStop(): Unit = {
    super.postStop()
  }

  override def receive = {
    case event: AmqpPublishEvent if amqp.isConfirmMode =>
      import event.req._
      log.debug(s"AmqpPublishEvent(${exchange.name}, $routingKey)")
      val eventId: Int = channel.getNextPublishSeqNo.toInt  // unsafe, but acceptable in realtime simulations
      try {
        eventQueue.request(eventId, event)
        channel.basicPublish(exchange.name, routingKey, properties, payload)
      } catch {
        case e: Exception =>
          log.error(s"basicPublish($exchange) failed", e)
      }

    case event: AmqpPublishEvent =>
      import event.req._
      log.debug(s"AmqpPublishEvent(${exchange.name}, $routingKey)")
      val eventId: Int = channel.getNextPublishSeqNo.toInt  // unsafe, but acceptable in realtime simulations
      try {
        eventQueue.request(eventId, event)
        channel.basicPublish(exchange.name, routingKey, properties, payload)
        eventQueue.accept(eventId)
      } catch {
        case e: Exception =>
          log.error(s"basicPublish($exchange) failed", e)
          eventQueue.reject(eventId, e)
      }

    case msg@ PublishAcked(no, multiple) =>
      log.debug(msg.toString.green)
      eventQueue.accept(no, multiple)

    case msg@ PublishNacked(no, multiple) =>
      log.warning(msg.toString.yellow)
      eventQueue.reject(no, multiple, Nacked)

    case req: PublishRequest =>
      import req._
      log.debug(s"PublishRequest(${exchange.name}, $routingKey)")
      super.interact(req) { ch =>
        ch.basicPublish(exchange.name, routingKey, properties, payload)
      }
  }
}
