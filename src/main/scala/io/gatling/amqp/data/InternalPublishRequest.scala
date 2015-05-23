package io.gatling.amqp.data

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.MessageProperties
import io.gatling.core.session.Session
import io.gatling.core.structure._

case class InternalPublishRequest(
  req: PublishRequest,
  session: Session
)
