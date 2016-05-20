package io.gatling.amqp.infra

import akka.actor._
import akka.routing._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.result.writer.StatsEngine
import io.gatling.core.session.Session

import scala.collection.mutable
import scala.util._

class AmqpRouter(statsEngine: StatsEngine)(implicit amqp: AmqpProtocol) extends Actor with Logging {
  private lazy val publishers: Router = {
    var p = Router(RoundRobinRoutingLogic(), Vector[Routee]())
    for (i <- 1 to amqp.connection.poolSize) {
      val name = s"AmqpPublisher-$i"
      p = p.addRoutee(context.actorOf(AmqpPublisher.props(name, amqp), name))
    }
    p
  }

  // create one consumer for one session
  private val consumerActors = mutable.HashMap[String, ActorRef]()  // UserId -> ref(AmqpConsumer)
  private def consumerActorFor(session: Session, req: ConsumeRequest): ActorRef = {
    val name = s"AmqpConsumer-user-${session.userId}"
    consumerActors.getOrElseUpdate(session.userId, {
      req match {
        case null =>
          log.warn("THIS SHOULD NOT HAPPEN! Check code. I will continue with returning some dummy actor, which will " +
            "be terminated just after its creation. Nothing wrong (just performance) will happen.")
          context.actorOf(AmqpConsumer.props(name, amqp), name)
        case ConsumeSingleMessageRequest(_, _, _, Some(_)) =>
          context.actorOf(AmqpConsumerCorrelation.props(name, amqp), name)
        case _ =>
          context.actorOf(AmqpConsumer.props(name, amqp), name)
      }
    })
  }

  def receive: Receive = {
    case m: AmqpPublishRequest =>
      publishers.route(m, sender())

    case m: AmqpConsumeRequest =>
      consumerActorFor(m.session, m.req).forward(m)

    case m: WaitTermination if consumerActors.isEmpty =>
      sender() ! Success("no consumers")

    case m: WaitTermination =>
      consumerActorFor(m.session, null).forward(m)
  }
}

object AmqpRouter {
  def props(statsEngine : StatsEngine, amqp: AmqpProtocol) = Props(classOf[AmqpRouter], statsEngine, amqp)
}