package io.gatling.amqp.event

import akka.actor._
import akka.event._
import com.rabbitmq.client.AMQP.BasicProperties
import io.gatling.amqp.data._
import io.gatling.core.session.Session

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
