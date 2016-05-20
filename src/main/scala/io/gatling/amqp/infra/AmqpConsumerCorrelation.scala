package io.gatling.amqp.infra

import akka.actor.Props
import io.gatling.amqp.config.AmqpProtocol
import io.gatling.amqp.data.{AsyncConsumerRequest, ConsumeSingleMessageRequest}
import io.gatling.amqp.event.AmqpConsumeRequest

/**
  * Created by Ľubomír Varga on 20.5.2016.
  */
class AmqpConsumerCorrelation(actorName: String)(implicit _amqp: AmqpProtocol) extends AmqpConsumerBase(actorName) {
  override def receive = super.receive.orElse {
    case AmqpConsumeRequest(req, session, next) =>
      req match {
        case req: AsyncConsumerRequest =>
          throw new RuntimeException("This actor is not right one for this type of command")

        case req: ConsumeSingleMessageRequest if req.correlationId.isEmpty =>
          throw new RuntimeException("This actor is not right one for this type of command")

        case req: ConsumeSingleMessageRequest if req.correlationId.isDefined =>
          ???
      }
  }

  override protected def isFinished: Boolean = ???

  override protected def shutdown(): Unit = ???
}

object AmqpConsumerCorrelation {
  def props(name: String, amqp: AmqpProtocol) = Props(classOf[AmqpConsumerCorrelation], name, amqp)
}
