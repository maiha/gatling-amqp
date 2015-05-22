package io.gatling.amqp.config

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.mutable.ArrayBuffer
import scala.util._

import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging

import io.gatling.amqp.data._
import pl.project13.scala.rainbow._

/**
 * preparations for AMQP Server
 */
trait AmqpPreparation { this: AmqpProtocol =>
  private val preparings: ArrayBuffer[AmqpMessage] = ArrayBuffer[AmqpMessage]()
  private val prepareTimeout: Timeout = Timeout(3 seconds)

  def prepare(msg: AmqpMessage): Unit = {
    preparings += msg
  }

  protected def awaitPreparation(): Unit = {
    for (msg <- preparings) {
      Await.result((manager ask msg)(prepareTimeout), Duration.Inf) match {
        case Success(m) => logger.info(s"amqp: $m".green)
        case Failure(e) => throw e
      }
    }
  }
}
