package io.gatling.amqp.infra

import akka.actor._
import pl.project13.scala.rainbow._

trait Logging extends Actor with ActorLogging {
  protected lazy val className = getClass.getSimpleName

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"amqp: Start actor `$className'".yellow)
  }

  override def postStop(): Unit = {
    log.info(s"amqp: Stop actor `$className'".yellow)
    super.postStop()
  }
}
