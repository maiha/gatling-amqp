package io.gatling.amqp.event

import akka.actor._
import akka.event._

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
