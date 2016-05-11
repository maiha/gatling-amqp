package io.gatling.amqp.infra

import akka.actor._
import io.gatling.amqp.config._
import io.gatling.amqp.event._
import io.gatling.core.result.message.{KO, OK, ResponseTimings, Status}
import io.gatling.core.result.writer.StatsEngine
import io.gatling.core.session.Session

case class MessageOk(event: AmqpPublishing, stoppedAt: Long, title: String)
case class MessageNg(event: AmqpPublishing, stoppedAt: Long, title: String, message: Option[String])
case class WriteStat(session: Session, startedAt: Long, stoppedAt: Long, title: String, status: Status, code: Option[String], mes: Option[String])

/**
 *  Publish stats log to the Gatling core DataWriter
 */
class AmqpTracer(statsEngine: StatsEngine)(implicit amqp: AmqpProtocol) extends Actor with Logging {
  def receive = {
    case WriteStat(session, startedAt, stoppedAt, title, status, code, mes) =>
      val timings = ResponseTimings(startedAt, stoppedAt, stoppedAt, stoppedAt)
      statsEngine.logResponse(session, title, timings, OK, code, mes)

    case MessageOk(event, stoppedAt, title) =>
      import event._
      val timings = ResponseTimings(startedAt, stoppedAt, stoppedAt, stoppedAt)
      statsEngine.logResponse(session, title, timings, OK, None, None)

    case MessageNg(event, stoppedAt, title, message) =>
      import event._
      val timings = ResponseTimings(startedAt, stoppedAt, stoppedAt, stoppedAt)
      statsEngine.logResponse(session, title, timings, KO, None, message)
  }
}
