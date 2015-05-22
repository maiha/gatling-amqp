package io.gatling.amqp.config

import akka.actor._
import io.gatling.amqp.data._
import io.gatling.amqp.infra._

/**
 * preparations for AMQP Server
 */
trait AmqpVariables { this: AmqpProtocol =>
  /**
   * mutable variables (initialized in warmUp)
   */
  private var systemOpt: Option[ActorSystem] = None
  private var manageOpt: Option[ActorRef]    = None
  private var routerOpt: Option[ActorRef]    = None

  def system  : ActorSystem = systemOpt.getOrElse{ throw new RuntimeException("ActorSystem is not defined yet") }
  def manager : ActorRef    = manageOpt.getOrElse{ throw new RuntimeException("manager is not defined yet") }
  def router  : ActorRef    = routerOpt.getOrElse{ throw new RuntimeException("router is not defined yet") }

  protected def setupVariables(system: ActorSystem): Unit = {
    systemOpt = Some(system)
    routerOpt = Some(system.actorOf(Props(new AmqpRouter()(this))))
    manageOpt = Some(system.actorOf(Props(new AmqpManager()(this))))
  }
}
