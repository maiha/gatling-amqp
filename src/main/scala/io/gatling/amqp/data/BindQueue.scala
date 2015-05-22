package io.gatling.amqp.data

// for e2q
case class BindQueue(
  exchange   : String,
  queue      : String,
  routingKey : String = "",
  arguments  : Arguments = DefaultArguments
) extends AmqpMessage

// TODO: support e2e
