package io.gatling.amqp.event

import akka.actor._
import akka.event._
import com.rabbitmq.client.AMQP.BasicProperties
import io.gatling.amqp.data._
import io.gatling.core.session.Session

sealed trait AmqpAction
object AmqpPublishAction extends AmqpAction
object AmqpConsumeAction extends AmqpAction

sealed trait AmqpEvent {
  def action: AmqpAction
}

/**
  * Publishing
  */
abstract class AmqpPublishEvent extends AmqpEvent {
  def action: AmqpAction = AmqpPublishAction
}
case class AmqpPublishRequest(req: PublishRequest, session: Session) extends AmqpPublishEvent

case class AmqpPublishing(publisherName: String, no: Int, startedAt: Long, req: PublishRequest, session: Session) extends AmqpPublishEvent {
  def eventId: String = s"$publisherName-$no"
}
case class AmqpPublished(publisherName: String, no: Int, stoppedAt: Long, event: AmqpPublishing) extends AmqpPublishEvent {
  def eventId: String = s"$publisherName-$no"
}
case class AmqpPublishFailed(publisherName: String, no: Int, stoppedAt: Long, e: Throwable) extends AmqpPublishEvent {
  def eventId: String = s"$publisherName-$no"
}
case class AmqpPublishAcked(publisherName: String, no: Int, multiple: Boolean, stoppedAt: Long) extends AmqpPublishEvent {
  def eventId: String = s"$publisherName-$no"
}
case class AmqpPublishNacked(publisherName: String, no: Int, multiple: Boolean, stoppedAt: Long) extends AmqpPublishEvent {
  def eventId: String = s"$publisherName-$no"
}

/**
  * Consuming
  */
abstract class AmqpConsumevent extends AmqpEvent {
  def action: AmqpAction = AmqpConsumeAction
}
case class AmqpConsumeRequest(req: ConsumeRequest, session: Session) extends AmqpConsumevent

case class AmqpConsuming(consumerName: String, no: Int, startedAt: Long, req: ConsumeRequest, session: Session) extends AmqpConsumevent {
  def eventId: String = s"$consumerName-$no"
}
case class AmqpConsumed(consumerName: String, no: Int, stoppedAt: Long, event: AmqpConsuming) extends AmqpConsumevent {
  def eventId: String = s"$consumerName-$no"
}
case class AmqpConsumeFailed(consumerName: String, no: Int, stoppedAt: Long, e: Throwable) extends AmqpConsumevent {
  def eventId: String = s"$consumerName-$no"
}
case class AmqpConsumeAcked(consumerName: String, no: Int, multiple: Boolean, stoppedAt: Long) extends AmqpConsumevent {
  def eventId: String = s"$consumerName-$no"
}
case class AmqpConsumeNacked(consumerName: String, no: Int, multiple: Boolean, stoppedAt: Long) extends AmqpConsumevent {
  def eventId: String = s"$consumerName-$no"
}
