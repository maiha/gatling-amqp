package io.gatling.amqp.config

import akka.pattern.ask
import akka.util.Timeout
import io.gatling.amqp.data._
import io.gatling.core.session.Session
import pl.project13.scala.rainbow._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util._

/**
 * termination for AMQP
 */
trait AmqpTermination { this: AmqpProtocol =>
  private val publishTimeout: Timeout = Timeout(1 hour)
  private val consumeTimeout: Timeout = Timeout(1 hour)

  protected def awaitTerminationFor(session: Session): Unit = {
    // wait nacker to ensure all confirms has been fired
    Await.result((nacker ask WaitTermination(session))(publishTimeout), Duration.Inf) match {
      case Success(m) => logger.debug(s"amqp: $m".green)
      case Failure(e) => throw e
    }

    // wait consumers
    Await.result((router ask WaitTermination(session))(consumeTimeout), Duration.Inf) match {
      case Success(m) => logger.debug(s"amqp: $m".green)
      case Failure(e) => throw e
    }
  }
}
