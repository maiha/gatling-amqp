package io.gatling.amqp.data

import akka.actor.ActorRef
import io.gatling.core.session.Session

case class WaitConfirms(ref: ActorRef, session: Session)
