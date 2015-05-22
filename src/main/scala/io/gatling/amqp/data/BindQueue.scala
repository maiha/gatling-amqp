package io.gatling.amqp.data

// for e2q
case class BindQueue(
  exchange   : String,
  queue      : String,
  routingKey : String = "",
  arguments  : Arguments = DefaultArguments
) extends AmqpChannelCommand

// TODO: support e2e
