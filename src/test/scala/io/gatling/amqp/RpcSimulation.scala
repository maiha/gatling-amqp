package io.gatling.amqp

import java.util.concurrent.atomic.AtomicLong

import com.rabbitmq.client.AMQP.BasicProperties
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
  * Echo service replies with received correlation id. Testing client (scenario testingEchoServiceScenario) is checking
  * if received reply does contain username, which was send in request to echo server.
  *
  * Note: example lacks of handling bad things (for example try to use session(key).asOption[Type] instead
  * of session(key).as[Type], or not checking, what is in reply to property)
  */
class RpcSimulation extends Simulation {
  private val log = LoggerFactory.getLogger(classOf[RpcSimulation])

  val USERNAME_KEY: String = "userName"
  val counter = new AtomicLong(0)
  val feeder = Iterator.continually(Map(USERNAME_KEY -> ("rpcClient-" + counter.getAndIncrement().toString)))

  val rpcClientsCount = 10
  val rpcCallsPerUser = 10
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

  val echoScenarioMockServicePart = scenario(s"mock echo server - reply to $echoCount messages")
    .repeat(echoCount) {
      exec {
        amqp("ConsumeSingleMsgForEchoing").consumeSingle(echoQueue.name, saveResultToSession = true)
      }
        // simulate server side processing time (if you pass ~5 seconds, consume on client side should timeout)
        .pause(0 seconds, 1 seconds)
        .exec {
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
              session(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY).as[DeliveredMsg].body),
            session => {
              val msg = session(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY).as[DeliveredMsg]
              val correlationId = msg.properties.getCorrelationId
              val prop = new BasicProperties.Builder
              import scala.collection.JavaConversions._
              prop.headers(Map("statusCode" -> 200.asInstanceOf[AnyRef]))
              if (correlationId != null && correlationId.nonEmpty) {
                log.info("Going to set correlation id to response. correlationId={}.", correlationId.yellow.asInstanceOf[Any])
                prop.correlationId(correlationId)
              }
              prop.build()
            })
        // we need to wait until all publish requests are actually published (publish does not block/wait) before ending scenario (we get Can't handle ResponseMessage ... in state Terminated)
      }
    }.pause(1500 millisecond)

  val testingEchoServiceScenario = scenario(s"Echo service test with $rpcCallsPerUser calls")
    .feed(feeder)
    .repeat(rpcCallsPerUser, "counterEchoTest") {
      // send request (rpcCall) to echo service
      exec(amqp("echo number ${counterEchoTest} request publish")
        .publishRpcCall(
          echoExchange.name,
          body = Left("Test message, echo number ${counterEchoTest}. My name is ${" + USERNAME_KEY + "}"),
          replyToProperty = Some(requestersQueue.name)
        )
      )
        .exec(amqp("echo number ${counterEchoTest} consume single reply")
          .consumeRpcResponse(requestersQueue.name)
        )
        .exitHereIfFailed
        .exec(session => {
          val msg = session(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY).as[DeliveredMsg]
          val msgBody = new String(msg.body)
          val user = session(USERNAME_KEY).as[String]
          if (false == msgBody.contains(user)) {
            log.warn("consumed msgBody={} as response for client {}. (this reply is not mine!)",
              msgBody.red.asInstanceOf[Any], user.blue.asInstanceOf[Any])
            session.markAsFailed
          } else {
            log.debug("consumed msgBody={} as response for client {}.", msgBody.asInstanceOf[Any], user)
            session
          }
        }).exitHereIfFailed
        .doIfEqualsOrElse("${" + USERNAME_KEY + "}", "rpcClient-4") {
          pause(0 seconds)
        } {
          pause(0 seconds, 350 milliseconds)
        }
    }

  setUp(
    echoScenarioMockServicePart.inject(atOnceUsers(1))
    , testingEchoServiceScenario.inject(nothingFor(200 milliseconds), atOnceUsers(rpcClientsCount))
  ).protocols(amqpProtocol)
}
