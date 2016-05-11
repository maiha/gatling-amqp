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
  private var publishers = Router(RoundRobinRoutingLogic(), Vector[Routee]())

  // create one consumer for one session
  private val consumerActors = mutable.HashMap[String, ActorRef]()  // UserId -> ref(AmqpConsumer)
  private def consumerActorFor(session: Session): ActorRef = {
    val name = s"AmqpConsumer-user-${session.userId}"
    consumerActors.getOrElseUpdate(session.userId, context.actorOf(AmqpConsumer.props(name, session, amqp), name))
  }

  override def preStart(): Unit = {
    super.preStart()
  }

  private def initializePublishersOnce(): Unit = {
    if (publishers.routees.isEmpty) {
      for(i <- 1 to amqp.connection.poolSize) { addPublisher(i) }
    }
  }

  def receive: Receive = {
    case m: AmqpPublishRequest =>
      initializePublishersOnce()
      publishers.route(m, sender())

    case m: AmqpConsumeRequest =>
      consumerActorFor(m.session).forward(m)

    case m: WaitTermination if consumerActors.isEmpty =>
      sender() ! Success("no consumers")

    case m: WaitTermination =>
      consumerActorFor(m.session).forward(m)

    case Terminated(ref) =>
      publishers = publishers.removeRoutee(ref)
  }

  private def addPublisher(i: Int): Unit = {
    val name = s"AmqpPublisher-$i"
    val ref = context.actorOf(AmqpPublisher.props(name, amqp), name)
    context watch ref
    publishers = publishers.addRoutee(ref)
  }
}

object AmqpRouter {
  def props(statsEngine : StatsEngine, amqp: AmqpProtocol) = Props(classOf[AmqpRouter], statsEngine, amqp)
}