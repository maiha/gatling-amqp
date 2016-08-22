package io.gatling.amqp.infra

import java.util.concurrent.atomic._

import akka.actor.{ActorRef, Props}
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.session.{Expression, Session}
import io.gatling.commons.util.TimeHelper.nowMillis
import io.gatling.core.action.Action
import pl.project13.scala.rainbow.Rainbow._

import scala.util._

// next step is called asynchronously from AmqpPublishAction, so this actor cannot change session.
class AmqpPublisher(actorName: String)(implicit amqp: AmqpProtocol) extends AmqpActor {
  private val nacker = amqp.nacker
  private val isConfirmMode = amqp.isConfirmMode
  private def sendEvent(event: AmqpEvent): Unit = nacker ! event
//  private def sendEvent(event: AmqpEvent): Unit = amqp.event.publish(event)

  override def preStart(): Unit = {
    super.preStart()
    if (isConfirmMode) {
      channel.confirmSelect()

      channel.addConfirmListener(new ConfirmListener() {
        def handleAck (no: Long, multi: Boolean): Unit = {
          sendEvent(AmqpPublishAcked(actorName, no.toInt, multi, nowMillis))
        }

        def handleNack(no: Long, multi: Boolean): Unit =
          sendEvent(AmqpPublishNacked(actorName, no.toInt, multi, nowMillis))
      })
    }
  }

  private lazy val localPublishSeqNoCounter = new AtomicInteger(1)
  private def getNextPublishSeqNo: Int = {
    if (isConfirmMode)
      channel.getNextPublishSeqNo.toInt
    else
      localPublishSeqNoCounter.getAndIncrement
  }

  def saveCorrelationIdInSessionAndResumeNext(r: RpcCallRequest, session: Session, next: Action, propertiesEvaluated: BasicProperties): Unit = {
    log.trace("Saving correlationId-> {}={} and going to execute next step.",
      AmqpPublisher.LAST_PUBLISHED_MESSAGE_CORRELATIONID_KEY.blue.asInstanceOf[AnyRef],
      propertiesEvaluated.getCorrelationId.red.asInstanceOf[AnyRef])
    next ! session.set(AmqpPublisher.LAST_PUBLISHED_MESSAGE_CORRELATIONID_KEY, propertiesEvaluated.getCorrelationId)
  }

  override def receive = {
    case AmqpPublishRequest(req, session, next) =>
      val propertiesEvaluated: BasicProperties = req.props.apply(session).get
      req match {
        case r: RpcCallRequest if next.isDefined => // TODO next HAVE TO BE defined here! move it from AmqpPublishRequest to RpcCallRequest
          saveCorrelationIdInSessionAndResumeNext(r, session, next.get, propertiesEvaluated)
        case _ =>
      }
      if (isConfirmMode) {
        publishAsync(req, session, propertiesEvaluated)
      } else {
        publishSync(req, session, propertiesEvaluated)
      }
  }

  def getData(session: Session, bytes: scala.Either[Expression[Array[Byte]], Array[Byte]]): Array[Byte] = {
    bytes match {
      case Left(l) => l.apply(session).get
      case Right(r) => r
    }
  }

  protected def publishSync(req: PublishRequest, session: Session, propertiesEvaluated: BasicProperties): Unit = {
    import req._
    val no: Int = getNextPublishSeqNo
    val event = AmqpPublishing(actorName, no, nowMillis, req, session)
    Try {
      val data: Array[Byte] = getData(session, bytes)
      val exchangeStr: String = exchange(session).get
      val routingKeyStr: String = routingKey(session).get
      channel.basicPublish(exchangeStr, routingKeyStr, propertiesEvaluated, data)
      //log.error("message {} published to exchange {}, routing queue {}. (empty exchange with queue in routing key causes publishing directly to queue)", data.toString.blue, exchangeStr.yellow, routingKeyStr.cyan) // import pl.project13.scala.rainbow._
    } match {
      case Success(_) =>
        sendEvent(AmqpPublished(actorName, no, nowMillis, event))
      case Failure(e) =>
        sendEvent(AmqpPublishFailed(actorName, no, nowMillis, e))
        log.error(s"basicPublish($exchange) failed", e)
    }
  }

  protected def publishAsync(req: PublishRequest, session: Session, propertiesEvaluated: BasicProperties): Unit = {
    import req._
    val no: Int = getNextPublishSeqNo
    sendEvent(AmqpPublishing(actorName, no, nowMillis, req, session))
    try {
      val data: Array[Byte] = getData(session, bytes)
      val exchangeStr: String = exchange(session).get
      val routingKeyStr: String = routingKey(session).get
      channel.basicPublish(exchangeStr, routingKeyStr, propertiesEvaluated, data)
      //log.error("message {} published to exchange {}, routing queue {}. (empty exchange with queue in routing key causes publishing directly to queue)", data.toString.blue, exchangeStr.yellow, routingKeyStr.cyan) // import pl.project13.scala.rainbow._
    } catch {
      case e: Exception =>
        sendEvent(AmqpPublishFailed(actorName, no, nowMillis, e))
        log.error(s"basicPublish($exchange) failed", e)
    }
  }
}

object AmqpPublisher {
  val LAST_PUBLISHED_MESSAGE_CORRELATIONID_KEY = "amqp_last_published_correlationid_msg"

  def props(name: String, amqp: AmqpProtocol) = Props(classOf[AmqpPublisher], name, amqp)
}