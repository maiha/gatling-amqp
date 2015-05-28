package io.gatling.amqp.action

import akka.actor._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.infra.Logging
import io.gatling.core.action.Chainable
import io.gatling.core.result.writer.StatsEngine
import io.gatling.core.session.Session
import io.gatling.core.util.TimeHelper.nowMillis
import pl.project13.scala.rainbow._
import java.util.concurrent.atomic._

class AmqpPublishAction(req: PublishRequest, tracker: ActorRef, val statsEngine: StatsEngine, val next: ActorRef)(implicit amqp: AmqpProtocol) extends Chainable with Logging {
  // message id as Int that should be unique number in whole simulation
  private val msgIdGenerator = new AtomicInteger()
  private def newEventId: Int = msgIdGenerator.getAndIncrement

  class AmqpTrackedPublishEvent(eventId: String, val req: PublishRequest, startedAt: Long, session: Session) extends AmqpPublishEvent {
    def onProcess(id: Int): Unit = {
      log.debug(s"event.onProcess($eventId)".green)
      tracker ! MessageSent(eventId, startedAt, nowMillis, session, next, "req")
    }

    def onSuccess(id: Int): Unit = {
      log.debug(s"event.onSuccess($eventId})".yellow)
      tracker ! MessageReceived(eventId, nowMillis, null)
    }

    def onFailure(id: Int, e: Throwable): Unit = {
      log.warning(s"event.onFailure($eventId, $e)".red)
      tracker ! MessageReceived(eventId, nowMillis, null)
    }
  }

  override def execute(session: Session): Unit = {
    val eventId = s"$newEventId(${session.userId})"
    amqp.router ! new AmqpTrackedPublishEvent(eventId, req, nowMillis, session)

    next ! session
  }
}
