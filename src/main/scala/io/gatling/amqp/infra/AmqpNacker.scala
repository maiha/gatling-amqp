package io.gatling.amqp.infra

import akka.actor._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.stats.StatsEngine
import pl.project13.scala.rainbow._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util._

class AmqpNacker(statsEngine: StatsEngine)(implicit amqp: AmqpProtocol) extends Actor with Logging {
  private val checkTerminationInterval = 1.second

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
    }

    /**
     * consuming accessors
     */

    def finished(n: Int, stoppedAt: Long, event: AmqpPublishing): Unit = {
      debugLog(s"finished($n)")
      amqp.tracer ! MessageOk(event, stoppedAt, "publish")
      consume(n)
    }

    def failed(n: Int, stoppedAt: Long, e: Throwable): Unit = {
      debugLog(s"failed($n)")
      amqp.tracer ! MessageNg(get(n), stoppedAt, e.getClass.getSimpleName, Some(e.toString))
      consume(n)
    }

    /**
     * multiple consuming accessors
     */

    def acked(n: Int, multiple: Boolean, stoppedAt: Long): Unit = {
      debugLog(s"acked($n)")
      running(n, multiple).foreach{ i =>
        amqp.tracer ! MessageOk(get(i), stoppedAt, "publish(ack)")
        consume(i)
      }
    }

    def nacked(n: Int, multiple: Boolean, stoppedAt: Long): Unit = {
      debugLog(s"nacked($n)")
      running(n, multiple).foreach{ i =>
        amqp.tracer ! MessageNg(get(i), stoppedAt, "publish(ack)", Some("nacked"))
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
    private var debugLogCnt = 100
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
    terminationWaitingActors.foreach{ case (ref, mes) => ref ! Success("all publishing requests successfully finished") }
    terminationWaitingActors.clear
  }

  private case class CheckTermination(interval: FiniteDuration)

  override def receive = {
    case mes@ WaitTermination(session) =>
      startCheckTerminationOnce()
      waitTermination(sender(), mes)

    case mes@ CheckTermination(interval) =>
      runningCount match {
        case 0 =>
          log.debug(s"CheckTermination: all publish requests finished".green)
          notifyTermination()
        case n =>
          log.debug(s"CheckTermination: waiting $n confirmations. re-check after($interval)".yellow)
          import scala.concurrent.ExecutionContext.Implicits.global
          context.system.scheduler.scheduleOnce(interval, self, mes)  // retry again after interval
      }

    case event@ AmqpPublishing(publisherName, no, req, startedAt, session) =>
      queueFor(publisherName).publish(no, event)

    case event@ AmqpPublished(publisherName, no, stoppedAt, publishing) =>
      queueFor(publisherName).finished(no, stoppedAt, publishing)

    case event@ AmqpPublishFailed(publisherName, no, stoppedAt, err) =>
      queueFor(publisherName).failed(no, stoppedAt, err)

    case event@ AmqpPublishAcked(publisherName, no, multiple, stoppedAt) =>
      queueFor(publisherName).acked(no, multiple, stoppedAt)

    case event@ AmqpPublishNacked(publisherName, no, multiple, stoppedAt) =>
      queueFor(publisherName).nacked(no, multiple, stoppedAt)

    case event: AmqpPublishEvent => // ignore other publish events
      log.warn("ignored event " + event)

    case event =>
      log.warn("ignored unknown event " + event)
  }
}

object AmqpNacker {
  def props(statsEngine : StatsEngine, amqp: AmqpProtocol) = Props(classOf[AmqpNacker], statsEngine, amqp)
}