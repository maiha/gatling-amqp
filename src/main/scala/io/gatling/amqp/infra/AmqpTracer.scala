package io.gatling.amqp.infra

import akka.actor._
import io.gatling.amqp.config._
import io.gatling.amqp.event._
import io.gatling.commons.stats.{KO, OK, Status}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings

case class MessageOk(event: AmqpPublishing, stoppedAt: Long, title: String)
case class MessageNg(event: AmqpPublishing, stoppedAt: Long, title: String, message: Option[String])
case class WriteStat(session: Session, startedAt: Long, stoppedAt: Long, title: String, status: Status, code: Option[String], mes: Option[String])

/**
 *  Publish stats log to the Gatling core DataWriter
 */
class AmqpTracer(statsEngine: StatsEngine)(implicit amqp: AmqpProtocol) extends Actor with Logging {
  override def receive = {
    case WriteStat(session, startedAt, stoppedAt, title, status, code, mes) =>
      val timings = ResponseTimings(startedAt, stoppedAt)
      statsEngine.logResponse(session, title, timings, status, code, mes)

    case MessageOk(event, stoppedAt, title) =>
      import event._
      val timings = ResponseTimings(startedAt, stoppedAt)
      statsEngine.logResponse(session, title + "-" + event.req.requestName(session).get, timings, OK, None, None)

    case MessageNg(event, stoppedAt, title, message) =>
      import event._
      val timings = ResponseTimings(startedAt, stoppedAt)
      statsEngine.logResponse(session, title + "-" + event.req.requestName(session).get, timings, KO, None, message)
  }
}

object AmqpTracer {
  def props(statsEngine : StatsEngine, amqp: AmqpProtocol) = Props(classOf[AmqpTracer], statsEngine, amqp)
}