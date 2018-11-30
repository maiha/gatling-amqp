package io.gatling.amqp.config

import akka.actor.ActorSystem
import io.gatling.core.CoreComponents
import io.gatling.core.protocol.ProtocolComponents
import io.gatling.core.session.Session

case class AmqpComponents(system: ActorSystem, coreComponents: CoreComponents, amqpProtocol: AmqpProtocol) extends ProtocolComponents {
  def onStart: Option[Session => Session] = Some(session => session)

  def onExit: Option[Session => Unit] =
    Some(amqpProtocol.userEnd)
}
