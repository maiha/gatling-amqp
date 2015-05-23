package io.gatling.amqp.infra

import akka.actor._
import akka.routing._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.core.result.writer.StatsEngine

class AmqpRouter(statsEngine: StatsEngine)(implicit amqp: AmqpProtocol) extends Actor with Logging {
  private var router = Router(RoundRobinRoutingLogic(), Vector[Routee]())

  override def preStart(): Unit = {
    super.preStart()
    for(i <- 1 to amqp.connection.poolSize) { addRoutee() }
  }

  def receive: Receive = {
    case m: PublishRequest =>
      router.route(m, sender())
    case m: InternalPublishRequest =>
      router.route(m, sender())
    case m: WaitConfirms =>
      router.route(Broadcast(m), sender())
    case Terminated(ref) =>
      router = router.removeRoutee(ref)
//      addRoutee
  }

  private def addRoutee(): Unit = {
    val ref = context.actorOf(Props(new AmqpPublisher(statsEngine)))
    context watch ref
    router = router.addRoutee(ref)
  }
}
