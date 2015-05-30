package io.gatling.amqp.data

case class DeclareQueue(queue: AmqpQueue) extends AmqpChannelCommand
