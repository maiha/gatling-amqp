package io.gatling.amqp.infra

import com.rabbitmq.client._
import com.rabbitmq.client.QueueingConsumer.Delivery
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.amqp.util._
import io.gatling.core.result.message.{KO, OK, ResponseTimings,Status}
import io.gatling.core.session.Session
import io.gatling.core.util.TimeHelper.nowMillis
import pl.project13.scala.rainbow._

trait Stats { this: AmqpActor =>
  implicit val amqp: AmqpProtocol
  private lazy val statsEngine = amqp.statsEngine

  protected def statsOk(session: Session, startedAt: Long, stoppedAt: Long, title: String, code: Option[String] = None): Unit = {
    amqp.tracer ! WriteStat(session, startedAt, stoppedAt, title, OK, code, None)
  }

  protected def statsNg(session: Session, startedAt: Long, stoppedAt: Long, title: String, code: Option[String], mes: String): Unit = {
    amqp.tracer ! WriteStat(session, startedAt, stoppedAt, title, KO, code, Some(mes))
  }
}
