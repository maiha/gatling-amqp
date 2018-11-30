package io.gatling.amqp.action

import akka.actor._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.amqp.request.builder.AmqpAttributes
import io.gatling.commons.stats.{KO, OK, Status}
import io.gatling.commons.util.ClockSingleton.nowMillis
import io.gatling.commons.validation.Validation
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings
import io.gatling.core.util.NameGen
import io.gatling.core.Predef._

class AmqpPublishAction(attributes: AmqpAttributes[PublishRequest], val statsEngine: StatsEngine, val next: Action)
                       (implicit amqpProtocol: AmqpProtocol)
  extends ExitableAction
  with NameGen {
  override def name: String = genName("AmqpPublishAction")

  override def execute(session: Session): Unit = recover(session) {
    publishMessage(session) {
      case (request, startDate) =>
        // done time
        val endDate = nowMillis
        executeNext(
          session,
          startDate,
          endDate,
          OK,
          next,
          attributes.requestName.apply(session).get,
          Some(new String(request.bytes, "UTF-8"))
        )
    }
  }

  private def publishMessage(session: Session)(postAction: (PublishRequest, Long) => Unit): Validation[Unit] = {
    val startDate = nowMillis
    attributes.payload(session).map { req =>
      amqpProtocol.router ! AmqpPublishRequest(req, session)
      postAction(req, startDate)
    }
  }

  private def executeNext(session:  Session,
                          sent:     Long,
                          received: Long,
                          status:   Status,
                          next:     Action,
                          title:    String,
                          message:  Option[String] = None
                         ): Unit = {
    val timings = ResponseTimings(sent, received)
    statsEngine.logResponse(session, title, timings, status, None, message)
    next ! session.logGroupRequest(timings.responseTime, status).increaseDrift(nowMillis - received)
  }
}

object AmqpPublishAction {
  def apply(attributes: AmqpAttributes[PublishRequest], statsEngine: StatsEngine, next: Action)
  (implicit amqpProtocol: AmqpProtocol): AmqpPublishAction = {
    new AmqpPublishAction(attributes, statsEngine, next)
  }
}