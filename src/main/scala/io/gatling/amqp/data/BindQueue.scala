package io.gatling.amqp.data

case class BindQueue(
  exchange  : AmqpExchange,
  queue     : AmqpQueue,
  routingKey: String = "",
  arguments : Arguments = DefaultArguments
) extends AmqpChannelCommand
