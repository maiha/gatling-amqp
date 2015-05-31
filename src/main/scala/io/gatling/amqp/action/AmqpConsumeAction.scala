package io.gatling.amqp.action

import akka.actor._
import io.gatling.amqp.event._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.infra.Logging
import io.gatling.core.action.Chainable
import io.gatling.core.result.writer.StatsEngine
import io.gatling.core.session.Session
import io.gatling.core.util.TimeHelper.nowMillis
import pl.project13.scala.rainbow._
import java.util.concurrent.atomic._
import scala.collection.mutable

class AmqpConsumeAction(req: ConsumeRequest, val next: ActorRef)(implicit amqp: AmqpProtocol) extends Chainable with Logging {
  override def execute(session: Session): Unit = {
    amqp.router ! AmqpConsumeRequest(req, session)

    next ! session
  }
}
