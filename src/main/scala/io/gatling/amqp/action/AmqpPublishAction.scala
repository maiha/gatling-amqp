package io.gatling.amqp.action

import akka.actor._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.action.{Action, ChainableAction}
import io.gatling.core.session.Session
import io.gatling.core.util.NameGen

class AmqpPublishAction(req: PublishRequest, val next: Action)(implicit amqp: AmqpProtocol) extends ChainableAction with NameGen {
  override def execute(session: Session): Unit = {
    amqp.router ! AmqpPublishRequest(req, session)

    next ! session
  }

  override def name: String = genName("AmqpPublishAction")
}
