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
    req match {
      case req: PublishRequestAsync =>
        amqp.router ! AmqpPublishRequest(req, session)
        next ! session

      case req: RpcCallRequest =>
        amqp.router ! AmqpPublishRequest(req, session, Some(next))
    }
  }
}

object AmqpPublishAction {
  def props(req: PublishRequest, next: ActorRef, amqp: AmqpProtocol) = Props(classOf[AmqpPublishAction], req, next, amqp)
}