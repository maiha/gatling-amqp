package io.gatling.amqp.data

case class AmqpExchange(
  name      : String,
  tpe       : String,
  durable   : Boolean   = true,
  autoDelete: Boolean   = true,
  arguments : Arguments = DefaultArguments
)
