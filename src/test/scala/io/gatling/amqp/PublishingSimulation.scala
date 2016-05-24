package io.gatling.amqp

import io.gatling.amqp.Predef._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.core.Predef._

import scala.concurrent.duration._

class PublishingSimulation extends Simulation {
  private val exchangePubSim: AmqpExchange = exchange("gatlingPublishingSimulation", "fanout", durable = true, autoDelete = false)
  private val queueQ1: AmqpQueue = queue("q1", durable = true, autoDelete = false)
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("localhost")
    .port(5672)
    // .vhost("/")
    .auth("guest", "guest")
    .poolSize(3)
    .declare(exchangePubSim)
    .declare(queueQ1)
    .bind(exchangePubSim, queueQ1)

    .confirmMode()

  // val body = Array.fill[Byte](1000*10)(1) // 1KB data for test
  val body = "{'x':1}"
  //val req = PublishRequestAsync("q1", body).persistent

  val scn  = scenario("AMQP Publish(ack)").repeat(1000) {
    exec(amqp("Publish").publish("q1", body = Right(body)))
  }

  setUp(scn.inject(rampUsers(3) over (1 seconds))).protocols(amqpProtocol)
}
