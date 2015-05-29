package io.gatling.amqp.infra

import akka.actor._
import akka.routing._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.result.writer.StatsEngine
import pl.project13.scala.rainbow._

class AmqpRouter(statsEngine: StatsEngine)(implicit amqp: AmqpProtocol) extends Actor with Logging {
  private var router = Router(RoundRobinRoutingLogic(), Vector[Routee]())

  override def preStart(): Unit = {
    super.preStart()
    for(i <- 1 to amqp.connection.poolSize) { addRoutee(i) }
  }

  def receive: Receive = {
    case m: PublishRequest =>
      router.route(m, sender())
    case m: AmqpPublishRequest =>
      router.route(m, sender())
//    case AwaitTermination(session) =>
//      router.route(Broadcast(m), sender())
    case Terminated(ref) =>
      router = router.removeRoutee(ref)
  }

  private def addRoutee(i: Int): Unit = {
    val name = s"AmqpPublisher-$i"
    val ref = context.actorOf(Props(new AmqpPublisher(name)), name)
    context watch ref
    router = router.addRoutee(ref)
  }
}
