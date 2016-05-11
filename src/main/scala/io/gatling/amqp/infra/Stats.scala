package io.gatling.amqp.infra

import io.gatling.amqp.config._
import io.gatling.commons.stats.{OK, KO}
import io.gatling.core.session.Session

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
