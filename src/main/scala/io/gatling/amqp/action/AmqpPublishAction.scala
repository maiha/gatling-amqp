package io.gatling.amqp.action

import akka.actor._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.amqp.infra.Logging
import io.gatling.core.action.Chainable
import io.gatling.core.session.Session

class AmqpPublishAction(req: PublishRequest, val next: ActorRef)(implicit amqp: AmqpProtocol) extends Chainable with Logging {
  override def execute(session: Session): Unit = {
    amqp.router ! AmqpPublishRequest(req, session)

    next ! session
  }
}

object AmqpPublishAction {
  def props(req: AmqpRequest, next: ActorRef, amqp: AmqpProtocol) = Props(classOf[AmqpPublishAction], req, next, amqp)
}