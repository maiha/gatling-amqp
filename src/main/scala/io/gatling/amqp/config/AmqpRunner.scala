package io.gatling.amqp.config

import akka.actor._
import io.gatling.core.controller.throttle.Throttler
import io.gatling.core.stats.{DataWritersStatsEngine, StatsEngine}
import pl.project13.scala.rainbow._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * run preparings in console
 */
trait AmqpRunner { this: AmqpProtocol =>
  def run(): Unit = {
    val system = ActorSystem("AmqpRunner")
    try {
      val statsEngine: StatsEngine = new DataWritersStatsEngine(system, Seq[ActorRef]())
      val throttler  : Throttler   = null  // just use manage Actor
      //warmUp(system, statsEngine, throttler)
    } catch {
      case e: Throwable =>
        // maybe failed to declare queue like inequivalent args
        if (e.getCause() != null)
          logger.error(s"failed: ${e.getCause}".red)
        else
          logger.error(s"failed: $e".red, e)
    } finally {
      Await.result(system.terminate(), Duration.Inf)
     }
  }
}
