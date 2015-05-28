package io.gatling.amqp.request

sealed trait AmqpDestination
case class AmqpQueue(name: String) extends AmqpDestination
case class AmqpTopic(name: String) extends AmqpDestination
