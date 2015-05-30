package io.gatling.amqp.data

case class AmqpQueue(
  name       : String,
  durable    : Boolean   = true,
  exclusive  : Boolean   = false,
  autoDelete : Boolean   = true,
  arguments  : Arguments = DefaultArguments
)
