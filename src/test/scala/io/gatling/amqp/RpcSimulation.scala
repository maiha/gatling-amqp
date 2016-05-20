package io.gatling.amqp

import io.gatling.amqp.Predef._
import io.gatling.amqp.config._
import io.gatling.amqp.data.{AmqpExchange, AmqpQueue}
import io.gatling.amqp.infra.AmqpConsumer
import io.gatling.amqp.infra.AmqpConsumer.DeliveredMsg
import io.gatling.core.Predef._
import org.slf4j.LoggerFactory
import pl.project13.scala.rainbow._

import scala.concurrent.duration._

/**
  * Note: example lacks of handling bad things (for example try to use session(key).asOption[Type] instead
  * of session(key).as[Type], or not checking, what is in reply to property)
  */
class RpcSimulation extends Simulation {
  private val log = LoggerFactory.getLogger(classOf[RpcSimulation])

  val rpcClientsCount = 1
  val rpcCallsPerUser = 100
  val echoCount = rpcClientsCount * rpcCallsPerUser

  private val echoExchange: AmqpExchange = exchange("gatlingTestEchoServiceExchange", "fanout", durable = true, autoDelete = false)
  private val echoQueue: AmqpQueue = queue("gatlingTestEchoServiceQueue", durable = true, autoDelete = false)
  private val requestersQueue: AmqpQueue = queue("gatlingTestEchoRequestersQueue", durable = true, autoDelete = false)

  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("localhost")
    .port(5672)
    .auth("guest", "guest")
    .declare(echoExchange) // for test echo service
    .declare(echoQueue) // for test echo service
    .bind(echoExchange, echoQueue) // for test echo service
    .declare(requestersQueue) // for requesters (reply to queue)

  val echoScenario = scenario(s"AMQP Echo for $echoCount messages")
    .repeat(echoCount) {
      exec {
        amqp("ConsumeSingleMsgForEchoing").consumeSingle(echoQueue.name, saveResultToSession = true)
      }.exec {
        //consume all messages which have left in queue
        amqp("PublishEchoedMsg")
          .publishToQueue(
            session => {
              val msg = session(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY).as[DeliveredMsg]
              val replyTo = msg.properties.getReplyTo
              log.info("Going to reply to queue {} with message {}.",
                replyTo.yellow.asInstanceOf[Any],
                new String(msg.body).blue.asInstanceOf[Any])
              replyTo
            },
            Left(session =>
              session(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY).as[DeliveredMsg].body))
        // we need to wait until all publish requests are actually published (publish does not block/wait) before ending scenario (we get Can't handle ResponseMessage ... in state Terminated)
      }
    }.pause(1500 millisecond)

  val testingEchoServiceScenario = scenario(s"Testing Echo service with $rpcCallsPerUser messages")
    .repeat(rpcCallsPerUser, "counterEchoTest") {
      exec(amqp("echo number ${counterEchoTest} request publish")
        .publish(echoExchange.name, Left("Test message, echo number ${counterEchoTest}"), Some(requestersQueue.name)))
        .exec(amqp("echo number ${counterEchoTest} consume single reply")
          .consumeSingle(requestersQueue.name, saveResultToSession = true))
        .exec(session => {
          val msg = session(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY).asOption[DeliveredMsg]
          log.info("consumed msg={}.", msg.asInstanceOf[Any])
          session
        })
    }

  setUp(
    echoScenario.inject(atOnceUsers(1))
    , testingEchoServiceScenario.inject(nothingFor(200 milliseconds), atOnceUsers(rpcClientsCount))
  ).protocols(amqpProtocol)
}
