package io.gatling.amqp.infra

import akka.actor._
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.QueueingConsumer.Delivery
import com.rabbitmq.client._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.amqp.infra.AmqpConsumer.DeliveredMsg
import io.gatling.core.session.Session
import io.gatling.commons.util.TimeHelper.nowMillis
import io.gatling.core.action.Action
import pl.project13.scala.rainbow._

import scala.util._

class AmqpConsumer(actorName: String)(implicit _amqp: AmqpProtocol) extends AmqpConsumerBase(actorName) {

  private var _consumer: Option[QueueingConsumer] = None
  private def consumer = _consumer.getOrElse{ throw new RuntimeException("[bug] consumer is not defined yet") }
  private var _consumerTag: Option[String] = None

  protected override def stopMessage: String = s"(delivered: $deliveredCount)"

  case class Delivered(startedAt: Long, stoppedAt: Long, delivery: DeliveredMsg)
  case class DeliveryTimeouted(msec: Long) extends RuntimeException

  override def preStart(): Unit = {
    super.preStart()
    _consumer = Some(new QueueingConsumer(channel))
  }

  private case class ConsumeRequested()

  private case class BlockingReadOne(session: Session, requestName: String)

  override def isFinished: Boolean = deliveredCount match {
    case 0 => (lastRequestedAt + initialTimeout < nowMillis)  // wait initial timeout for first publishing
    case n => (lastDeliveredAt + runningTimeout < nowMillis)  // wait running timeout for last publishing
  }

  override def receive = super.receive.orElse {
    case BlockingReadOne(session, requestName) =>
      tryNextDelivery(deliveryTimeout) match {
        case Success(delivered: Delivered) => deliveryFound(delivered, session, "consume" + "-" + requestName)
        case Failure(DeliveryTimeouted(msec)) => deliveryTimeouted(msec)
        case Failure(error)                   => deliveryFailed(error)
      }
      self ! BlockingReadOne(session, requestName)

    case AmqpConsumeRequest(req, session, next) =>
      req match {
        case req: AsyncConsumerRequest if req.autoAck == true => {
          //exec next action and asynchronously (from gatling scenario point of view) start consuming everything in queue
          next ! session
          consumeSync(req.queue, session, req.requestName.apply(session).get)
        }
        case req: AsyncConsumerRequest if req.autoAck == false => {
          //exec next action and asynchronously (from gatling scenario point of view) start consuming everything in queue
          next ! session
          consumeAsync(req)
        }
        case req: ConsumeSingleMessageRequest if req.correlationId.isEmpty =>
          consumeSingle(req, session, next);

        case req: ConsumeSingleMessageRequest if req.correlationId.isDefined =>
          throw new RuntimeException("This actor is not right one for this type of command")
      }
  }

  override def shutdown(): Unit = {
    _consumerTag.foreach(tag => {
      channel.basicCancel(tag)
      log.debug(s"Cancel consumer($tag)".yellow)
    })
    context.become({case _ =>}, discardOld = true)
    context.stop(self)  // ignore all rest messages
  }

  /**
    * Method will try to use basicGet, which is nonblocking "get if any" style method. It returns null value, if there
    * is no message in queue. If basicGet does not return message, method will register/start
    * (see [[justInitSyncConsumer()]], basicConsume) asynchronous consumer which will start consuming messages
    * in background and try to receive one message from its queue with timeout.
    *
    * @param req
    * @param session
    * @param next
    */
  protected def consumeSingle(req: ConsumeSingleMessageRequest, session: Session, next: Action): Unit = {
    val startAt = nowMillis
    val getSingle: GetResponse = channel.basicGet(req.queueName, req.autoAck)

    def processResponse(consumedBy: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) = {
      val endAt = nowMillis
      // save delivered message into session if requested so
      val newSession = if (req.saveResultToSession) {
        session.set(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY, DeliveredMsg(envelope, properties, body))
      } else {
        session
      }
      statsOk(newSession, startAt, endAt, "consumeSingleBy" + consumedBy + "-" + req.requestName)
      try {
        if (req.autoAck == false) {
          channel.basicAck(envelope.getDeliveryTag, false)
        }
      } catch {
        case ex: Throwable =>
          log.warn("Error while ack/cancel msg/consumer. Going to continue with next step.", ex)
      } finally {
        next ! newSession
      }
    }

    if (getSingle == null) {
      if (req.autoAck == true) {
        if (_consumerTag == None) {
          log.info("Going to start consumer, because basicGet did not returned any data.")
          justInitSyncConsumer(req.queueName)
        }
      } else {
        ???
      }
      // TODO introduce req.delivery timeout
      tryNextDelivery(deliveryTimeout) match {
        case Success(delivered: Delivered) =>
          processResponse("Consumer", delivered.delivery.envelope, delivered.delivery.properties, delivered.delivery.body)

        case Failure(ex) =>
          try {
            val endAt = nowMillis
            ex match {
              case DeliveryTimeouted(msec) =>
                statsNg(session, startAt, endAt, "consumeSingle" + "-" + req.requestName, None, s"DeliveryTimeouted($msec)")
                deliveryTimeouted(msec)
              case error =>
                statsNg(session, startAt, endAt, "consumeSingle" + "-" + req.requestName, None, s"error=${error.getMessage}")
                deliveryFailed(error)
            }
          } finally {
            next ! session.markAsFailed
          }
      }
    } else {
      // else for getSingle == null
      processResponse("BasicConsume", getSingle.getEnvelope, getSingle.getProps, getSingle.getBody)
    }
  }

  /**
    * Initialize basic consume with auto acknowledgement of received messages (thus sync in method name).
    *
    * @param queueName
    */
  protected def justInitSyncConsumer(queueName: String) = {
    val tag = channel.basicConsume(queueName, true, consumer)
    _consumerTag = Some(tag)
    log.debug(s"Start basicConsume($queueName) [tag:$tag]".yellow)
  }

  protected def consumeSync(queueName: String, session: Session, requestName: String): Unit = {
    justInitSyncConsumer(queueName)
    self ! BlockingReadOne(session, requestName)
  }

  protected def tryNextDelivery(timeoutMsec: Long): Try[Delivered] = Try {
    lastRequestedAt = nowMillis
    val nextDelivery: Delivery = consumer.nextDelivery(timeoutMsec)
    if (nextDelivery == null) {
      throw DeliveryTimeouted(timeoutMsec)
    }
    lastDeliveredAt = nowMillis
    Success(Delivered(lastRequestedAt, lastDeliveredAt, DeliveredMsg(nextDelivery)))
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

  protected def deliveryFound(delivered: Delivered, session: Session, descriptionWithRequestNameAlso: String): Unit = {
    deliveredCount += 1
//    val message = new String(delivery.getBody())
    import delivered._
    //    val tag = delivery.getEnvelope.getDeliveryTag
    statsOk(session, startedAt, stoppedAt, descriptionWithRequestNameAlso)
//    log.debug(s"$actorName.consumeSync: got $tag".red)
  }
}

object AmqpConsumer {
  /**
    * Key for session attributes which holds delivered message. It is instance of {@link com.rabbitmq.client.GetResponse}.
    */
  val LAST_CONSUMED_MESSAGE_KEY = "amqp_last_consumed_msg"
  def props(name: String, amqp: AmqpProtocol) = Props(classOf[AmqpConsumer], name, amqp)

  /**
    * Simple case class holding delivered message. It holds exactly same things as in {@link com.rabbitmq.client.QueueingConsumer.Delivery}
    * and nearly all information from {@link com.rabbitmq.client.GetResponse}.
    *
    * @param envelope
    * @param properties
    * @param body
    */
  case class DeliveredMsg(envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte])

  object DeliveredMsg {
    def apply(delivery: com.rabbitmq.client.QueueingConsumer.Delivery): DeliveredMsg = new DeliveredMsg(delivery.getEnvelope, delivery.getProperties, delivery.getBody)

    def apply(getMsg: com.rabbitmq.client.GetResponse): DeliveredMsg = new DeliveredMsg(getMsg.getEnvelope, getMsg.getProps, getMsg.getBody)
  }
}