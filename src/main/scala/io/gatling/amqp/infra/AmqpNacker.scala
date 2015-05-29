package io.gatling.amqp.infra

import akka.actor._

import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.amqp.util._
import io.gatling.core.result.writer.StatsEngine
import io.gatling.core.util.TimeHelper.nowMillis
import pl.project13.scala.rainbow._

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util._

class AmqpNacker(statsEngine: StatsEngine)(implicit amqp: AmqpProtocol) extends AmqpActor {

  object Nacked extends RuntimeException
  private val checkTerminationInterval = 500.milliseconds

  class TracedQueue(name: String) {
    private val bit = new mutable.BitSet()           // index as bit
    private val map = mutable.OpenHashMap[Int, AmqpPublishing]()  // index -> A

    private val publishedFact = new mutable.BitSet()        // published
    private val consumedFact  = new mutable.BitSet()        // consumed

    def size    = bit.size
    def isEmpty = bit.isEmpty

    def publish(n: Int, event: AmqpPublishing): Unit = {
      debugLog(s"publish($n)")

      // log.info(s"publish($n)".green)
      bit.add(n)
      map.put(n, event)

      publishedFact.add(n)

      import event._
      amqp.tracer ! MessageSent(eventId, startedAt, startedAt, session, "req")
    }

    /**
     * consuming accessors
     */

    def finished(n: Int, stoppedAt: Long): Unit = {
      debugLog(s"finished($n)")
      amqp.tracer ! MessageReceived(get(n).eventId, stoppedAt, null)
      consume(n)
    }

    def failed(n: Int, stoppedAt: Long, e: Throwable): Unit = {
      debugLog(s"failed($n)")
      amqp.tracer ! MessageReceived(get(n).eventId, stoppedAt, null)
      consume(n)
    }

    /**
     * multiple consuming accessors
     */

    def accepted(n: Int, multiple: Boolean, stoppedAt: Long): Unit = {
      debugLog(s"accepted($n)")
      running(n, multiple).foreach{ i =>
        amqp.tracer ! MessageReceived(get(i).eventId, stoppedAt, null)
        consume(i)
      }
    }

    def rejected(n: Int, multiple: Boolean, stoppedAt: Long, e: Throwable): Unit = {
      debugLog(s"rejected($n)")
      running(n, multiple).foreach{ i =>
        amqp.tracer ! MessageReceived(get(i).eventId, stoppedAt, null)
        consume(i)
      }
    }

    protected def running(n: Int, multiple: Boolean): Iterator[Int] = {
      if (multiple)
        bit.takeWhile(_ <= n).filter(contains).iterator
      else
        Iterator[Int](n)
    }

    protected def consume(n: Int): Unit = {
      bit.remove(n)
      map.remove(n)
      consumedFact.add(n)
    }

    private val contains = bit.contains _
    private def get(n: Int): AmqpPublishing = map.getOrElse(n,
      throw new RuntimeException(s"[BUG] TracedQueue($name).get($n) not found: ${bitInfoFor(n)}".red))
    private def bitInfoFor(n: Int): String = s"p=${publishedFact.size},c=${consumedFact.size} map(${map.contains(n)}), bit(${bit.contains(n)}), publishedFact(${publishedFact.contains(n)}), consumedFact(${consumedFact.contains(n)})"

    private val debugLogMax = 0  // disable
    private var debugLogCnt = 0
    private def debugLog(method: String): Unit = {
      debugLogCnt += 1
      if (debugLogCnt <= debugLogMax) {
        log.info(s"TracedQueue($name).$method called".red)
      }
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    amqp.event.subscribe(self, AmqpPublishAction)
  }

  private val queues = mutable.HashMap[String, TracedQueue]()
  private def queueFor(name: String): TracedQueue = queues.getOrElseUpdate(name, new TracedQueue(name))
  private def isRunning = queues.values.find(_.size > 0).isDefined
  private def runningCount: Int = queues.values.map(_.size).sum

  private val terminationWaitingActors = mutable.HashMap[ActorRef, Any]()
  private def waitTermination(ref: ActorRef, mes: Any) = terminationWaitingActors.put(ref, mes)
  private def startCheckTerminationOnce(): Unit = {
    if (terminationWaitingActors.isEmpty)
      self ! CheckTermination(checkTerminationInterval)
  }

  private def notifyTermination(): Unit = {
    terminationWaitingActors.foreach{ case (ref, mes) => ref ! Success(mes) }
    terminationWaitingActors.clear
  }

  private case class CheckTermination(interval: FiniteDuration)

  override def receive = {
    case mes@ WaitConfirms(session) =>
      startCheckTerminationOnce()
      waitTermination(sender(), mes)

    case mes@ CheckTermination(interval) =>
      runningCount match {
        case 0 =>
          log.debug(s"CheckTermination: all requests finished".green)
          notifyTermination()
        case n =>
          log.debug(s"CheckTermination: waiting $n confirmations. re-check after($interval)".yellow)
          system.scheduler.scheduleOnce(interval, self, mes)  // retry again after interval
      }

    case event@ AmqpPublishing(publisherName, no, req, startedAt, session) =>
      queueFor(publisherName).publish(no, event)

    case event@ AmqpPublished(publisherName, no, stoppedAt) =>
      queueFor(publisherName).finished(no, stoppedAt)

    case event@ AmqpPublishFailed(publisherName, no, stoppedAt, err) =>
      queueFor(publisherName).failed(no, stoppedAt, err)

    case event@ AmqpPublishAcked(publisherName, no, multiple, stoppedAt) =>
      queueFor(publisherName).accepted(no, multiple, stoppedAt)

    case event@ AmqpPublishNacked(publisherName, no, multiple, stoppedAt) =>
      queueFor(publisherName).rejected(no, multiple, stoppedAt, Nacked)

    case event: AmqpPublishEvent =>  // ignore other publish events
  }
}
