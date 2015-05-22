package io.gatling.amqp

import akka.actor._
import io.gatling.amqp.action._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.core.Predef._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext

import io.gatling.amqp.Predef._
import scala.concurrent.duration._

class PublishingSimulation extends Simulation {
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("localhost")
    .port(5672)
    .auth("guest", "guest")
    .poolSize(10)
    // .prepare(DeclareQueue("q1", autoDelete = false)) // TODO: implement this dsl

  val scn = scenario("RabbitMQ Publishing").repeat(1000) {
    exec(
      amqp("Publish")
        .publish("q1", payload = "{foo:1}")
    )
  }

  setUp(scn.inject(rampUsers(10) over (3 seconds))).protocols(amqpProtocol)
}


