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
abstract class AmqpPublishEvent extends AmqpEvent {
  def action: AmqpAction = AmqpPublishAction
}
case class AmqpPublishRequest(req: PublishRequest, session: Session) extends AmqpPublishEvent

case class AmqpPublishing(publisherName: String, no: Int, req: PublishRequest, startedAt: Long, session: Session) extends AmqpPublishEvent {
  def eventId: String = s"$publisherName-$no"
}
case class AmqpPublished(publisherName: String, no: Int, stoppedAt: Long) extends AmqpPublishEvent {
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

class AmqpEventBus extends ActorEventBus with LookupClassification {
  type Event      = AmqpEvent
  type Classifier = AmqpAction

  override protected def classify(event: Event): Classifier = event.action

  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event
  }

  override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int = {
    a.compareTo(b)
  }

  override protected def mapSize(): Int = 128
}
