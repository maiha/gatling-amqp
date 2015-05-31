package io.gatling.amqp

import io.gatling.amqp.Predef._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.core.Predef._

import scala.concurrent.duration._

class ConsumingSimulation extends Simulation {
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("amqp")
    .port(5672)
    .auth("guest", "guest")

  val scn = scenario("AMQP Consume").exec {
    amqp("Consume").consume("q1", autoAck = true)
  }

  setUp(scn.inject(atOnceUsers(1))).protocols(amqpProtocol)
}
