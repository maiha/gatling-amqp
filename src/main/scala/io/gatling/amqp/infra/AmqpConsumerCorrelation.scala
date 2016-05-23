package io.gatling.amqp.infra

import akka.actor.{ActorRef, Props}
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{Consumer, Envelope, ShutdownSignalException}
import io.gatling.amqp.config.AmqpProtocol
import io.gatling.amqp.data.{AsyncConsumerRequest, ConsumeSingleMessageRequest}
import io.gatling.amqp.event.{AmqpConsumeRequest, AmqpSingleConsumerPerStepRequest}
import io.gatling.amqp.infra.AmqpConsumer.DeliveredMsg
import io.gatling.core.Predef._
import io.gatling.core.util.TimeHelper.nowMillis
import pl.project13.scala.rainbow.Rainbow._

import scala.collection.mutable
import scala.concurrent.duration._

/**
  * TODO once per some time, check pending requests if some timeouted, just resume next with session marked as failed.
  *
  * Created by Ľubomír Varga on 20.5.2016.
  */
class AmqpConsumerCorrelation(actorName: String)(implicit _amqp: AmqpProtocol) extends AmqpConsumerBase(actorName) {
  /**
    * If consumer gets some message from queue and according its correlation id it have no next action to be executed,
    * it will reschedule received message to be processed again (new lookup to [[routingMap]]) after given time period.
    * If no next action is found after second processing, received message is dropped with warning log and KO status.
    */
  val retryDelay: FiniteDuration = 500 milliseconds

  case class ReceivedData(deliveredAt: Long, correlationId: String, deliveredMsg: DeliveredMsg)

  case class ReceivedDataRepeatedDelivery(receivedData: ReceivedData)

  case class RequestWithCorrelation(session: Session, nextAction: ActorRef, saveResultToSession: Boolean, requestTimestamp: Long)

  val routingMap = mutable.HashMap[String, RequestWithCorrelation]()
  //correlationId -> (session, next)
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
      self ! ReceivedData(deliveredAt, correlationId, deliveredMsg)
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
  private def handleReceivedData(receivedData: ReceivedData): Boolean = {
    val value = routingMap.remove(receivedData.correlationId)
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
        nextActor ! newSession
        statsOk(newSession, req.requestTimestamp, receivedData.deliveredAt, "consumeSingleWithCorrelationId")
        true
      }
      case None =>
        false
    }
  }

  override def receive = super.receive.orElse {
    case rd: ReceivedData =>
      if (false == handleReceivedData(rd)) {
        scheduler.scheduleOnce(retryDelay, self, ReceivedDataRepeatedDelivery(rd))
      }

    case ReceivedDataRepeatedDelivery(rd) =>
      if (true == handleReceivedData(rd)) {
        log.info("Message delivered in repeated delivery. Response was received before actually awaited by scenario. Times for this response will be possibly wrong.")
      } else {
        log.warn("Received message witch correlationId={}, which has not been requested. I have waited {} but no request came from scenario. Going to ignore that message. " +
          "Possible problem is that you use same queue in multiple steps of scenario or your queue is spammed by some unrelated messages. msg={}.",
          rd.correlationId.yellow.asInstanceOf[AnyRef], retryDelay.asInstanceOf[AnyRef], new String(rd.deliveredMsg.body).blue.asInstanceOf[AnyRef])
        // Should I statsNg, when it is not KO for some step? Probably no.
        //statsNg(null.asInstanceOf[Session], deliveredAt, nowMillis, "consumeSingleWithCorrelationId", None, "No request was made for received message with correlationId=\"" + correlationId + "\". See log for more info.")
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
          routingMap.put(actualCorrelationId, RequestWithCorrelation(session, next, req.saveResultToSession, requestTimestamp))
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

  override protected def shutdown(): Unit = {
    tagMap.foreach {
      x =>
        val tag = x._2
        channel.basicCancel(tag)
        log.debug(s"Cancel consumer($tag)".yellow)
    }

    // TODO clean up routingMap and log statsNg for all waiting requests

    context.become({ case _ => }, discardOld = true)
    context.stop(self) // ignore all rest messages
  }
}

object AmqpConsumerCorrelation {
  def props(name: String, amqp: AmqpProtocol) = Props(classOf[AmqpConsumerCorrelation], name, amqp)
}
