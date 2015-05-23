package io.gatling.amqp.action

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.infra.Logging
import io.gatling.core.action.Chainable
import io.gatling.core.session.Session
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.TimeHelper.nowMillis
import pl.project13.scala.rainbow._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Failure

class PublishAction(val next: ActorRef, ctx: ScenarioContext, req: PublishRequest)(implicit amqp: AmqpProtocol) extends Chainable with Logging {
  override def execute(session: Session) {
    for (i <- 1 to req.count) {
      amqp.router ! InternalPublishRequest(req, session)
    }

    next ! session
  }
}
