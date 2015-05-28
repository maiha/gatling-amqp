package io.gatling.amqp.action

import akka.actor._
import io.gatling.amqp.action._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.infra._
import io.gatling.amqp.request._
import io.gatling.amqp.request.builder._
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.Expression
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.result.writer.StatsEngine

import scala.collection.mutable

import io.gatling.core.Predef.Session
import io.gatling.core.akka.BaseActor
import io.gatling.core.check.Check
import io.gatling.core.result.message.{KO, OK, ResponseTimings,Status}
import io.gatling.core.util.TimeHelper.nowMillis
import io.gatling.core.validation.Failure
import io.gatling.amqp._

import akka.actor.{ ActorRef, Props }

/**
  *  Advise actor a message was sent to AMQP provider
  *  @author jasonk@bluedevel.com
  */
case class MessageSent(
  requestId: String,
  startSend: Long,
  endSend: Long,
  session: Session,
  next: ActorRef,
  title: String)

/**
  *  Advise actor a response message was received from AMQP provider
  */
case class MessageReceived(responseId: String, received: Long, message: Message)

object AmqpRequestTrackerActor {
  def props(statsEngine: StatsEngine) = Props(new AmqpRequestTrackerActor(statsEngine))
}


/**
  *  Bookkeeping actor to correlate request and response AMQP messages
  *  Once a message is correlated, it publishes to the Gatling core DataWriter
  */
class AmqpRequestTrackerActor(statsEngine: StatsEngine) extends BaseActor with Logging {

  // messages to be tracked through this HashMap - note it is a mutable hashmap
  val sentMessages = mutable.HashMap.empty[String, (Long, Long, Session, ActorRef, String)]
  val receivedMessages = mutable.HashMap.empty[String, (Long, Message)]

  // Actor receive loop
  def receive = {

    // message was sent; add the timestamps to the map
    case MessageSent(corrId, startSend, endSend, session, next, title) =>
      receivedMessages.get(corrId) match {
        case Some((received, message)) =>
          // message was received out of order, lets just deal with it
          processMessage(session, startSend, received, endSend, message, next, title)
          receivedMessages -= corrId

        case None =>
          // normal path
          val sentMessage = (startSend, endSend, session, next, title)
          sentMessages += corrId -> sentMessage
      }
    // message was received; publish to the datawriter and remove from the hashmap
    case MessageReceived(corrId, received, message) =>
      sentMessages.get(corrId) match {
        case Some((startSend, endSend, session, next, title)) =>
          processMessage(session, startSend, received, endSend, message, next, title)
          sentMessages -= corrId

        case None =>
          // failed to find message; early receive? or bad return correlation id?
          // let's add it to the received messages buffer just in case
          val receivedMessage = (received, message)
          receivedMessages += corrId -> receivedMessage
      }
  }

  /**
    *  Processes a matched message
    */
  def processMessage(session: Session,
                     startSend: Long,
                     received: Long,
                     endSend: Long,
                     message: Message,
                     next: ActorRef,
                     title: String): Unit = {

      def executeNext(updatedSession: Session, status: Status, message: Option[String] = None) = {
        val timings = ResponseTimings(startSend, endSend, endSend, received)
        statsEngine.logResponse(updatedSession, title, timings, status, None, message)
//        next ! updatedSession.logGroupRequest((received - startSend).toInt, status).increaseDrift(nowMillis - received)
      }

/*

    // run all the checks, advise the Gatling API that it is complete and move to next
    val (checkSaveUpdate, error) = Check.check(message, session, checks)
    val newSession = checkSaveUpdate(session)
    error match {
      case None                   => executeNext(newSession, OK)
      case Some(Failure(message)) => executeNext(newSession.markAsFailed, KO, Some(message))
    }
 */

    // assume all responses ok
    executeNext(session, OK)
  }
}
