package io.gatling.amqp.action

import akka.actor._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.action.{Action, ChainableAction}
import io.gatling.core.session.Session
import io.gatling.core.util.NameGen

class AmqpPublishAction(req: AmqpRequest, val next: Action)(implicit amqp: AmqpProtocol) extends ChainableAction with NameGen {
  override def execute(session: Session): Unit = {
    req match {
      case x: PublishRequest =>
        amqp.router ! AmqpPublishRequest(x, session)
      case x: ConsumeRequest =>
        amqp.router ! AmqpConsumeRequest(x, session)
      case x =>
        logger.warn("unknown request! Going to ignore it. req=", x.asInstanceOf[AnyRef])
    }
    //next ! session
  }

  override def name: String = genName("AmqpPublishAction")
}

// TODO remove if not needed..
object AmqpPublishAction {
  def props(req: PublishRequest, next: Action): Props = Props(classOf[AmqpPublishAction], req, next)

  def props(req: ConsumeRequest, next: Action): Props = Props(classOf[AmqpPublishAction], req, next)
}
