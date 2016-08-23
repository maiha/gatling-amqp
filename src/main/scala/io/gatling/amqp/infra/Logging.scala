package io.gatling.amqp.infra

import akka.actor._
import com.typesafe.scalalogging.StrictLogging
import pl.project13.scala.rainbow._

trait Logging extends Actor with StrictLogging {
  protected lazy val className = getClass.getSimpleName
  protected val log = logger // gap between LazyLogging and ActorLogging
  protected def stopMessage: String = ""

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"amqp: Start actor `$className'".yellow)
  }

  override def postStop(): Unit = {
    log.info(s"amqp: Stop actor `$className' $stopMessage".yellow)
    super.postStop()
  }

  override def receive: Receive = {
    ???
  }
}
