package io.gatling.amqp.config

import akka.pattern.ask
import akka.util.Timeout
import io.gatling.amqp.data._
import io.gatling.core.session.Session
import pl.project13.scala.rainbow._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util._

/**
 * preparations for AMQP Server
 */
trait AmqpPreparation { this: AmqpProtocol =>
  private val prepareTimeout: Timeout = Timeout(3 seconds)

  protected def awaitPreparation(): Unit = {
    for (msg <- preparings) {
      Await.result((manage ask msg)(prepareTimeout), Duration.Inf) match {
        case Success(m) => logger.info(s"amqp: $m".green)
        case Failure(e) => throw e
      }
    }
  }
}
