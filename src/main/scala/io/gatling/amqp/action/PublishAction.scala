package io.gatling.amqp.action

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import io.gatling.amqp.config._
import io.gatling.amqp.data.PublishRequest
import io.gatling.core.action.Chainable
import io.gatling.core.result.message.{KO, OK, ResponseTimings, Status}
import io.gatling.core.session.Session
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.TimeHelper.nowMillis

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Failure

class PublishAction(val next: ActorRef, ctx: ScenarioContext, req: PublishRequest)(implicit amqp: AmqpProtocol) extends Chainable with ActorLogging {
  override def execute(session: Session) {
    var startedAt : Long = 0L
    var finishedAt: Long = 0L
    val timeout = Timeout(10 seconds)
    var status: Status = OK
    var errorMessage: Option[String] = None
    try {
      startedAt = nowMillis
      Await.result((amqp.router ask req)(timeout), Duration.Inf) match {
        case Failure(e) => throw e
        case _ =>
      }
    } catch {
      case e: Exception =>
        errorMessage = Some(e.getMessage)
        log.error("Unable to publish", e)
        status = KO
    } finally {
      finishedAt = nowMillis

      val timings = ResponseTimings(startedAt, finishedAt, finishedAt, finishedAt)
      val requestName = "RabbitMQ Publishing"

      val sec = (finishedAt - startedAt)/1000.0
      log.debug(s"$toString: timings=$timings ($sec)")
      ctx.statsEngine.logResponse(session, requestName, timings, status, None, errorMessage)

      next ! session
    }
  }
}
