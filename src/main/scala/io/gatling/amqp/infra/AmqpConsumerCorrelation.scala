package io.gatling.amqp.infra

import java.util.concurrent.TimeUnit

import akka.actor.Props
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{Consumer, Envelope, ShutdownSignalException}
import io.gatling.amqp.config.AmqpProtocol
import io.gatling.amqp.data.{AsyncConsumerRequest, ConsumeSingleMessageRequest}
import io.gatling.amqp.event.{AmqpConsumeRequest, AmqpSingleConsumerPerStepRequest}
import io.gatling.amqp.infra.AmqpConsumer.DeliveredMsg
import io.gatling.commons.util.TimeHelper
import io.gatling.commons.util.TimeHelper.nowMillis
import io.gatling.core.Predef._
import io.gatling.core.action.Action
import pl.project13.scala.rainbow.Rainbow._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try

/**
  * Created by Ľubomír Varga on 20.5.2016.
  */
class AmqpConsumerCorrelation(actorName: String,
                              val conv: Option[AmqpConsumerCorrelation.ReceivedData => String]
                             )(implicit _amqp: AmqpProtocol) extends AmqpConsumerBase(actorName) {
  if (conv.isDefined) {
    log.trace("This AmqpConsumerCorrelation instance will apply customCorrelationIdTransformer function to received data and " +
      "use result as correlation id of received message instead of its actual correlation id property this={}.", this)
  }

  /**
    * If consumer gets some message from queue and according its correlation id it have no next action to be executed,
    * it will reschedule received message to be processed again (new lookup to [[routingMap]]) after given time period.
    * If no next action is found after second processing, received message is dropped with warning log and KO status.
    */
  val retryDelay: FiniteDuration = 10 seconds

  /**
    * correlationId -> RequestWithCorrelation(session, next, ...)
    */
  val routingMap = mutable.HashMap[String, AmqpConsumerCorrelation.RequestWithCorrelation]()

  val tagMap = mutable.HashMap[String, String]() //queue name -> consumer tag

  // TODO use autoAck...
  def consumer(autoAck: Boolean): Consumer = new Consumer {
    override def handleCancel(consumerTag: String): Unit = ???

    override def handleRecoverOk(consumerTag: String): Unit = ???

    override def handleCancelOk(consumerTag: String): Unit = ???

    override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]): Unit = {
      val deliveredAt = nowMillis
      val correlationId: String = properties.getCorrelationId
      val deliveredMsg: DeliveredMsg = DeliveredMsg(envelope, properties, body)
      self ! AmqpConsumerCorrelation.ReceivedData(deliveredAt, correlationId, deliveredMsg)
    }

    override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit = {}

    override def handleConsumeOk(consumerTag: String): Unit = {}
  }

  /**
    * Handles just positive scenario (i.e. request for received message exists in [[routingMap]].
    *
    * @param receivedData
    * @return returns true, if message was delivered to right action. False is returned, if no such request exist in [[routingMap]], which have same correlation id a received data
    */
  private def handleReceivedData(receivedData: AmqpConsumerCorrelation.ReceivedData): Boolean = {
    val receivedCorrelationId = conv match {
      case None =>
        receivedData.correlationId
      case Some(x) =>
        x.apply(receivedData)
    }
    val value: Option[AmqpConsumerCorrelation.RequestWithCorrelation] = routingMap.remove(receivedCorrelationId)
    value match {
      case Some(req) => {
        deliveredCount += 1
        // if save was true, save it to session before sending session (moving to next action)
        val newSession = req.saveResultToSession match {
          case true =>
            req.session.set(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY, receivedData.deliveredMsg).set(AmqpPublisher.LAST_PUBLISHED_MESSAGE_CORRELATIONID_KEY, null)
          case false =>
            req.session.set(AmqpPublisher.LAST_PUBLISHED_MESSAGE_CORRELATIONID_KEY, null)
        }
        val nextActor = req.nextAction
        // TODO temporary solution for making statusCode=2XX only OK consumed messages. Messages without status codes will be now threated as OK messages
        val statusCode = Try(receivedData.deliveredMsg.properties.getHeaders.get("statusCode").asInstanceOf[Int]).getOrElse(299)
        if (200 >= statusCode && statusCode <= 299) {
          statsOk(newSession, req.requestTimestamp, receivedData.deliveredAt, getTitleForRequest(req.requestName))
        } else {
          statsNg(newSession, req.requestTimestamp, receivedData.deliveredAt, getTitleForRequest(req.requestName), Some(statusCode + ""), "Status code was " + statusCode)
        }
        nextActor ! newSession
        true
      }
      case None =>
        false
    }
  }

  log.info("Going to schedule TimeoutCheck to be executed each second.")
  val scheduleForTimeouts = context.system.scheduler.schedule(AmqpConsumerCorrelation.TIMEOUT_TRESHOLD, 1 seconds, self, AmqpConsumerCorrelation.TimeoutCheck)

  /**
    * Number of unexpected messages to be logged. This is counter, which will be decremented and stops on 0.
    */
  var unexpectedMsgLogged: Long = 7

  override def receive = super.receive.orElse {
    case AmqpConsumerCorrelation.TimeoutCheck =>
      val now = TimeHelper.nowMillis
      routingMap.foreach {
        case (corrId, req) => {
          val diff = FiniteDuration.apply(now - req.requestTimestamp, TimeUnit.MILLISECONDS)
          if (diff.gteq(AmqpConsumerCorrelation.TIMEOUT_TRESHOLD)) {
            log.warn("Timeout for corrId={}, has happened. diff={}.", corrId, diff)
            routingMap.remove(corrId)
            val newSession = req.session.markAsFailed
            statsNg(newSession, req.requestTimestamp, now, getTitleForRequest(req.requestName), Some("ConsumeReqTimeout"), "Consume request timeouted")
            req.nextAction ! newSession
          }
        }
      }

    case rd: AmqpConsumerCorrelation.ReceivedData =>
      if (false == handleReceivedData(rd)) {
        scheduler.scheduleOnce(retryDelay, self, AmqpConsumerCorrelation.ReceivedDataRepeatedDelivery(rd))
      }

    case AmqpConsumerCorrelation.ReceivedDataRepeatedDelivery(rd) =>
      if (true == handleReceivedData(rd)) {
        log.info("Message delivered in repeated delivery. Response was received before actually awaited by scenario. Times for this response will be possibly wrong.")
      } else {
        if (unexpectedMsgLogged > 0) {
          val a = unexpectedMsgLogged match {
            case 1 =>
              "This is last warning! All subsequent warnings of this type will be suppressed. "
            case _ =>
              ""
          }
          log.warn("{}Received message witch correlationId={}, which has not been requested. I have waited {} but no request came from scenario. Going to ignore that message. " +
            "Possible problem is that you use same queue in multiple steps of scenario or your queue is spammed by some unrelated messages. msg={}.",
            a.asInstanceOf[AnyRef], rd.correlationId.yellow.asInstanceOf[AnyRef], retryDelay.asInstanceOf[AnyRef], new String(rd.deliveredMsg.body).blue.asInstanceOf[AnyRef])
          // Should I statsNg, when it is not KO for some step? Probably no.
          //statsNg(null.asInstanceOf[Session], deliveredAt, nowMillis, "consumeSingleWithCorrelationId", None, "No request was made for received message with correlationId=\"" + correlationId + "\". See log for more info.")
          unexpectedMsgLogged -= 1
        }
      }

    case AmqpConsumeRequest(req, session, next) =>
      req match {
        case req: AsyncConsumerRequest =>
          throw new RuntimeException("This actor is not right one for this type of command")

        case req: ConsumeSingleMessageRequest if req.correlationId.isEmpty =>
          throw new RuntimeException("This actor is not right one for this type of command")

        case req: ConsumeSingleMessageRequest if req.correlationId.isDefined =>
          log.warn("Check amqp plugin code! Going to correctly continue, but...")
          self ! AmqpSingleConsumerPerStepRequest(req, session, next)
      }

    case AmqpSingleConsumerPerStepRequest(req, session, next) =>
      val requestTimestamp = nowMillis
      // TODO implement
      req match {
        case req: AsyncConsumerRequest =>
          throw new RuntimeException("This actor is not right one for this type of command")

        case req: ConsumeSingleMessageRequest if req.correlationId.isEmpty =>
          throw new RuntimeException("This actor is not right one for this type of command")

        case req: ConsumeSingleMessageRequest if req.correlationId.isDefined =>
          val currentConsumer = tagMap.getOrElseUpdate(req.queueName, {
            // register only once per queue
            val c = consumer(req.autoAck)
            val tag = channel.basicConsume(req.queueName, req.autoAck, c)
            log.debug(s"Start basicConsume(${req.queueName}) [tag:$tag] for AmqpConsumerCorrelation.".yellow)
            tag
          })
          val actualCorrelationId: String = req.correlationId.get(session).get
          val reqNameEvaluated: String = req.requestName.apply(session).get
          routingMap.put(actualCorrelationId, AmqpConsumerCorrelation.RequestWithCorrelation(session, next, req.saveResultToSession, requestTimestamp, reqNameEvaluated))
          log.trace("We have marked request for rpc response with correlationId={};next={}.",
            actualCorrelationId.red.asInstanceOf[AnyRef],
            next.asInstanceOf[AnyRef])
      }
  }


  override protected def isFinished: Boolean = {
    routingMap.isEmpty && (deliveredCount match {
      case 0 => (lastRequestedAt + initialTimeout < nowMillis) // wait initial timeout for first publishing
      case n => (lastDeliveredAt + runningTimeout < nowMillis) // wait running timeout for last publishing
    })
  }

  private def getTitleForRequest(requestName: String) = {
    //"ConsumeCorrelated" + "-" + requestName
    "Consume" + "-" + requestName
  }

  override protected def shutdown(): Unit = {
    context.become({ case _ => }, discardOld = true)

    scheduleForTimeouts.cancel()

    tagMap.foreach {
      x =>
        val tag = x._2
        channel.basicCancel(tag)
        log.debug(s"Cancel consumer($tag)".yellow)
    }

    // clean up routingMap and log statsNg for all waiting requests
    val shutdownTime = nowMillis
    if (routingMap.nonEmpty) {
      log.warn(s"There are still ${routingMap.size} requests in routingMap. They will be all finished as un-served due shutdown.")
    }
    routingMap.foldLeft(Unit)((_, unservedRequest) => {
      val correlationId = unservedRequest._1
      val request: AmqpConsumerCorrelation.RequestWithCorrelation = unservedRequest._2
      statsNg(request.session,
        request.requestTimestamp,
        shutdownTime,
        getTitleForRequest(request.requestName),
        Some("Shutting down remaining un-served requests."),
        "Request to consume message with correlationId=\"" + correlationId + "\" remained un-served till shutdown. Going to fail it and remove."
      )
      routingMap.remove(correlationId)
      Unit
    })

    context.stop(self) // ignore all rest messages
  }
}

object AmqpConsumerCorrelation {
  def props(name: String,
            conv: Option[AmqpConsumerCorrelation.ReceivedData => String],
            amqp: AmqpProtocol
           ) = Props(classOf[AmqpConsumerCorrelation], name, conv, amqp)

  case class ReceivedData(deliveredAt: Long, correlationId: String, deliveredMsg: DeliveredMsg)

  case class ReceivedDataRepeatedDelivery(receivedData: ReceivedData)

  case class RequestWithCorrelation(session: Session, nextAction: Action, saveResultToSession: Boolean, requestTimestamp: Long, requestName: String)

  /**
    * Command which is used to check timeouts of pending consume requests.
    */
  case object TimeoutCheck

  val TIMEOUT_TRESHOLD: FiniteDuration = 60 seconds
}
