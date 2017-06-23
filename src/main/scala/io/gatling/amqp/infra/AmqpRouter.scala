package io.gatling.amqp.infra

import akka.actor._
import akka.routing._
import io.gatling.amqp.config._
import io.gatling.amqp.data._
import io.gatling.amqp.event._
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

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
  private val consumerActors = mutable.HashMap[String, ActorRef]() // UserId -> ref(AmqpConsumer)

  /**
    *
    * @param session             session for which consumer is beeing find
    * @return right consumer for given session and type of consuming (with or without correlation id provided)
    */
  private def consumerActorFor(session: Session): ActorRef = {
    val name = s"AmqpConsumer-user-${session.userId}"
    consumerActors.getOrElseUpdate(name, context.actorOf(AmqpConsumer.props(name, amqp), name))
  }

  override def receive: Receive = {
    case m: AmqpPublishRequest =>
      publishers.route(m, sender())

    case m: AmqpConsumeRequest =>
      m.req match {
        case _: AsyncConsumerRequest =>
          consumerActorFor(m.session).forward(m)
        case req:ConsumeSingleMessageRequest if req.correlationId.isEmpty =>
          consumerActorFor(m.session).forward(m)
        case req:ConsumeSingleMessageRequest if req.correlationId.isDefined =>
          // req.correlationId is used to get correlation id from session. To match incoming message, have a look at consumerActorFor method.
          //consumerActorFor(m.session, true).forward(m)
          println(m + "aaaa")
      }

    case m: WaitTermination if consumerActors.isEmpty =>
      sender() ! Success("no consumers")

    case m: WaitTermination =>
      consumerActorFor(m.session).forward(m)
  }
}

object AmqpRouter {
  def props(statsEngine: StatsEngine, amqp: AmqpProtocol) = Props(classOf[AmqpRouter], statsEngine, amqp)
}