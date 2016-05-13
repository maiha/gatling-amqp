package io.gatling.amqp.infra

import akka.actor._
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.QueueingConsumer.Delivery
import com.rabbitmq.client._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.session.Session
import io.gatling.core.util.TimeHelper.nowMillis
import pl.project13.scala.rainbow._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util._

class AmqpConsumer(actorName: String)(implicit _amqp: AmqpProtocol) extends AmqpActor with Stats {
  implicit val amqp: AmqpProtocol = _amqp

  val checkTerminationInterval = 1.second
  val initialTimeout  = 60 * 1000  // msec (1 min)
  val deliveryTimeout = 1 * 1000  // msec
  val runningTimeout  = 2 * 1000  // assumes that no messages queued when this time past from lastDeliveredAt

  private var _consumer: Option[QueueingConsumer] = None
  private def consumer = _consumer.getOrElse{ throw new RuntimeException("[bug] consumer is not defined yet") }
  private var _consumerTag: Option[String] = None

  private var lastRequestedAt: Long = 0
  private var lastDeliveredAt: Long = 0
  private var deliveredCount : Long = 0
  private def notConsumedYet: Boolean = deliveredCount == 0

  protected override def stopMessage: String = s"(delivered: $deliveredCount)"

  case class Delivered(startedAt: Long, stoppedAt: Long, delivery: Delivery)
  case class DeliveryTimeouted(msec: Long) extends RuntimeException

  override def preStart(): Unit = {
    super.preStart()
    _consumer = Some(new QueueingConsumer(channel))
  }

  private case class ConsumeRequested()
  private case class BlockingReadOne(session: Session)

  private def isFinished: Boolean = deliveredCount match {
    case 0 => (lastRequestedAt + initialTimeout < nowMillis)  // wait initial timeout for first publishing
    case n => (lastDeliveredAt + runningTimeout < nowMillis)  // wait running timeout for last publishing
  }

  private val terminationWaitingActors = mutable.HashMap[ActorRef, Any]()
  private def waitTermination(ref: ActorRef, mes: Any) = terminationWaitingActors.put(ref, mes)
  private def startCheckTerminationOnce(): Unit = {
    if (terminationWaitingActors.isEmpty)
      self ! CheckTermination(checkTerminationInterval)
  }

  private def notifyTermination(msg: String): Unit = {
    terminationWaitingActors.foreach{ case (ref, mes) => ref ! Success(msg) }
    terminationWaitingActors.clear
  }

  private case class CheckTermination(interval: FiniteDuration)

  override def receive = {
    case mes@ WaitTermination(session) =>      
      startCheckTerminationOnce()
      waitTermination(sender(), mes)

    case mes@ CheckTermination(interval) =>
      isFinished match {
        case true =>
          val msg = "all message have been delivered"
          log.debug(msg.green)
          notifyTermination(msg)
          shutdown()
        case false =>
          if (! notConsumedYet)
            log.debug(s"CheckTermination: waiting delivered. re-check after($interval)".yellow)
          context.system.scheduler.scheduleOnce(interval, self, mes)  // retry again after interval
      }

    case BlockingReadOne(session) =>
      tryNextDelivery(deliveryTimeout) match {
        case Success(delivered: Delivered)    => deliveryFound(delivered, session)
        case Failure(DeliveryTimeouted(msec)) => deliveryTimeouted(msec)
        case Failure(error)                   => deliveryFailed(error)
      }
      self ! BlockingReadOne(session)

    case AmqpConsumeRequest(req, session, next) =>
      req match {
        case req: AsyncConsumerRequest => {
          //exec next action and asynchronously (from gatling scenario point of view) start consuming everything in queue
          next ! session
          if (req.autoAck)
            consumeSync(req.queue, session)
          else
            consumeAsync(req)
        }
        case req: ConsumeSingleMessageRequest =>
          consumeSingle(req, session, next);

      }
  }

  private def shutdown(): Unit = {
    _consumerTag.foreach(tag => {
      channel.basicCancel(tag)
      log.debug(s"Cancel consumer($tag)".yellow)
    })
    context.become({case _ =>}, discardOld = true)
    context.stop(self)  // ignore all rest messages
  }

  protected def consumeSingle(req: ConsumeSingleMessageRequest, session: Session, next: ActorRef): Unit = {
    val startAt = nowMillis
    channel.basicConsume(req.queue, req.autoAck, new Consumer {
      override def handleCancel(consumerTag: String): Unit = ???

      override def handleRecoverOk(consumerTag: String): Unit = ???

      override def handleCancelOk(consumerTag: String): Unit = {}

      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]): Unit = {
        val endAt = nowMillis
        // save delivered message into session if requested so
        val newSession = if (req.saveResultToSession) {
          session.set(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY, new GetResponse(envelope, properties, body, -1))
        } else {
          session
        }
        statsOk(newSession, startAt, endAt, "consumeSingle")
        if (req.autoAck == false) {
          channel.basicAck(envelope.getDeliveryTag, false)
        }
        channel.basicCancel(consumerTag)
        //executing next action have to be AFTER canceling consumer, because it is possible (and likely) that await termination will be faster end stops chanel before
        next ! newSession
      }

      override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit = {
        val endAt = nowMillis
        statsNg(session, startAt, endAt, "consumeSingle", None, "handleShutdownSignal")
      }

      override def handleConsumeOk(consumerTag: String): Unit = {}
    })
  }

  protected def consumeSync(queueName: String, session: Session): Unit = {
    val tag = channel.basicConsume(queueName, true, consumer)
    _consumerTag = Some(tag)
    log.debug(s"Start basicConsume($queueName) [tag:$tag]".yellow)
    self ! BlockingReadOne(session)
  }

  protected def tryNextDelivery(timeoutMsec: Long): Try[Delivered] = Try {
    lastRequestedAt = nowMillis
    val delivery: Delivery = consumer.nextDelivery(timeoutMsec)
    if (delivery == null) {
      throw DeliveryTimeouted(timeoutMsec)
    }

    lastDeliveredAt = nowMillis
    Success(Delivered(lastRequestedAt, lastDeliveredAt, delivery))
  }.flatten

  protected def consumeAsync(req: ConsumeRequest): Unit = {
    ???
  }

  protected def deliveryTimeouted(msec: Long): Unit = {
    if (! notConsumedYet)
      log.debug(s"$actorName delivery timeouted($msec)")
  }
  
  protected def deliveryFailed(err: Throwable): Unit = {
    log.warn(s"$actorName delivery failed: $err".yellow)
  }
  
  protected def deliveryFound(delivered: Delivered, session: Session): Unit = {
    deliveredCount += 1
//    val message = new String(delivery.getBody())
    import delivered._
    //    val tag = delivery.getEnvelope.getDeliveryTag
    statsOk(session, startedAt, stoppedAt, "consume")
//    log.debug(s"$actorName.consumeSync: got $tag".red)
  }
}

object AmqpConsumer {
  /**
    * Key for session attributes which holds delivered message. It is instance of {@link com.rabbitmq.client.GetResponse}.
    */
  val LAST_CONSUMED_MESSAGE_KEY = "amqp_last_consumed_msg"
  def props(name: String, amqp: AmqpProtocol) = Props(classOf[AmqpConsumer], name, amqp)
}