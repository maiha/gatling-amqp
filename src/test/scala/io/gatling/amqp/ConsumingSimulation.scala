package io.gatling.amqp

import com.rabbitmq.client.QueueingConsumer.Delivery
import io.gatling.amqp.Predef._
import io.gatling.amqp.config._
import io.gatling.amqp.infra.AmqpConsumer
import io.gatling.core.Predef._

class ConsumingSimulation extends Simulation {
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("localhost")
    .port(5672)
    .auth("guest", "guest")

  val printConsumedMessages = true

  val scn = scenario("AMQP Consume")
    .exec {
      amqp("Consume").consume("q1")
    }.doIf(printConsumedMessages) {
    // just for printing consumed messages
    exec(session => {
      val msg = session(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY).asOption[Delivery]
      println("consumed msg = " + msg)
      session
    })
  }

  setUp(scn.inject(atOnceUsers(1))).protocols(amqpProtocol)
}
