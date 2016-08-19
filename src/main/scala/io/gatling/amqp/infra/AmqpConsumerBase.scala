package io.gatling.amqp.infra

import akka.actor.ActorRef
import io.gatling.amqp.config.AmqpProtocol
import io.gatling.amqp.data.WaitTermination
import pl.project13.scala.rainbow._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Success

/**
  * Common base for consumer, which supports things around termination. Also some common parameters and state attributes
  * are present (as [[AmqpConsumerBase!.deliveryTimeout]] or [[AmqpConsumerBase!.deliveredCount]]).
  *
  * Note: Do not forget to implement your receive block in such way, that this class receive is also in game. For example
  * you can do it like this:
  *
  * {{{
  *   override def receive = super.receive.orElse {
  *     case x: String =>
  *       doSomething(x)
  *   }
  * }}}
  *
  * Created by Ľubomír Varga on 20.5.2016.
  */
abstract class AmqpConsumerBase(actorName: String)(implicit _amqp: AmqpProtocol) extends AmqpActor with Stats {
  implicit val amqp: AmqpProtocol = _amqp

  val checkTerminationInterval = 1.second
  val initialTimeout = 60 * 1000
  // msec (1 min)
  val deliveryTimeout = 1 * 1000
  // msec
  val runningTimeout = 2 * 1000 // assumes that no messages queued when this time past from lastDeliveredAt

  protected var lastRequestedAt: Long = 0
  protected var lastDeliveredAt: Long = 0
  protected var deliveredCount: Long = 0

  protected def notConsumedYet: Boolean = deliveredCount == 0

  protected def isFinished: Boolean

  protected def shutdown(): Unit

  private val terminationWaitingActors = mutable.HashMap[ActorRef, Any]()

  private def waitTermination(ref: ActorRef, mes: Any) = terminationWaitingActors.put(ref, mes)

  private def startCheckTerminationOnce(): Unit = {
    if (terminationWaitingActors.isEmpty)
      self ! CheckTermination(checkTerminationInterval)
  }

  private def notifyTermination(msg: String): Unit = {
    terminationWaitingActors.foreach { case (ref, mes) => ref ! Success(msg) }
    terminationWaitingActors.clear
  }

  private case class CheckTermination(interval: FiniteDuration)

  override def receive: PartialFunction[Any, Unit] = {
    case mes@WaitTermination(session) =>
      startCheckTerminationOnce()
      waitTermination(sender(), mes)

    case mes@CheckTermination(interval) =>
      isFinished match {
        case true =>
          val msg = "all message have been delivered"
          log.debug(msg.green)
          notifyTermination(msg)
          shutdown()
        case false =>
          if (!notConsumedYet)
            log.debug(s"CheckTermination: waiting delivered. re-check after($interval)".yellow)
          context.system.scheduler.scheduleOnce(interval, self, mes) // retry again after interval
      }
  }
}
