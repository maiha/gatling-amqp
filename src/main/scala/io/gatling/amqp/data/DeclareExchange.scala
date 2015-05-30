package io.gatling.amqp.data

case class DeclareExchange(exchange: AmqpExchange) extends AmqpChannelCommand
