package io.gatling.amqp.action

import akka.actor.{ActorRef, Props}
import io.gatling.amqp.config.AmqpProtocol
import io.gatling.amqp.data.{ConsumeRequest, ConsumeSingleMessageRequest}
import io.gatling.amqp.event.{AmqpConsumeRequest, AmqpSingleConsumerPerStepRequest}
import io.gatling.amqp.infra.{AmqpConsumerCorrelation, Logging}
import io.gatling.core.action.Chainable
import io.gatling.core.session.Session

/**
  * Created by Ľubomír Varga on 20.5.2016.
  */
class AmqpConsumeCorrelatedAction(req: ConsumeRequest,
                                  val next: ActorRef,
                                  val conv: Option[AmqpConsumerCorrelation.ReceivedData => String]
                                 )(implicit amqp: AmqpProtocol) extends Chainable with Logging {
  val consumerActorForCorrelationId: ActorRef = {
    // single actor for all users in this scenario step
    val name = "AmqpConsumerCorrelation"
    context.actorOf(AmqpConsumerCorrelation.props(name, conv, amqp), name)
  }

  override def execute(session: Session): Unit = {
    // router creates actors (AmqpConsumer) per session. For consuming message by correlation id, we need just one actor
    // per scenario step, thus just one AmqpConsumerCorrelation
    req match {
      case ConsumeSingleMessageRequest(_, _, _, _, Some(_), _) =>
        consumerActorForCorrelationId ! AmqpSingleConsumerPerStepRequest(req, session, next)
      case _ =>
        // TODO check request type in instantiation time, not in runtime
        // router will create single actor for this scenario step, for each user/session
        log.warn("There is something wrong. In single step of scenario there seems to be two different consume call types " +
          "(correlation one and one without). Weird. Check code. this should not happen. Going to continue correctly.")
        amqp.router ! AmqpConsumeRequest(req, session, next)
    }
  }
}

object AmqpConsumeCorrelatedAction {
  def props(req: ConsumeRequest,
            next: ActorRef,
            conv: Option[AmqpConsumerCorrelation.ReceivedData => String],
            amqp: AmqpProtocol
           ) = Props(classOf[AmqpConsumeCorrelatedAction], req, next, conv, amqp)
}