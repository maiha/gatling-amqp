package io.gatling.amqp.data

case class DeclareExchange(
  name: String,
  tpe: String,
  durable: Boolean,
  autoDelete: Boolean,
  arguments: Arguments
) extends AmqpMessage

