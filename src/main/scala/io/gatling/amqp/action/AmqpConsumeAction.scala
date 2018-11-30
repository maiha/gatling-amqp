package io.gatling.amqp.action

import akka.actor._
import com.typesafe.scalalogging.StrictLogging
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.amqp.infra.Logging
import io.gatling.amqp.request.builder.AmqpAttributes
import io.gatling.commons.stats.{KO, OK, Status}
import io.gatling.commons.util.ClockSingleton.nowMillis
import io.gatling.commons.validation.Validation
import io.gatling.core.action.{Action, ChainableAction, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings
import io.gatling.core.util.NameGen

class AmqpConsumeAction(attributes: AmqpAttributes[ConsumeRequest], val statsEngine: StatsEngine, val next: Action)
                       (implicit amqpProtocol: AmqpProtocol)
  extends ExitableAction
    with NameGen {
  override def name: String = "AmqpConsumeAction"

  override def execute(session: Session): Unit = recover(session) {
    consumeMessage(session) {
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
          None
        )
    }
  }


  private def consumeMessage(session: Session)(postAction: (ConsumeRequest, Long) => Unit): Validation[Unit] = {
    val startDate = nowMillis
    attributes.payload(session).map { req =>
      amqpProtocol.router ! AmqpConsumeRequest(req, session)
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

object AmqpConsumeAction {
  def apply(attributes: AmqpAttributes[ConsumeRequest], statsEngine: StatsEngine, next: Action)
           (implicit amqp: AmqpProtocol): AmqpConsumeAction = {
    new AmqpConsumeAction(attributes, statsEngine, next)
  }
}