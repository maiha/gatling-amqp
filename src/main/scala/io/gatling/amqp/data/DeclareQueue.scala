package io.gatling.amqp.data

case class DeclareQueue(
  name       : String,
  durable    : Boolean   = true,
  exclusive  : Boolean   = false,
  autoDelete : Boolean   = true,
  arguments  : Arguments = DefaultArguments
) extends AmqpMessage
