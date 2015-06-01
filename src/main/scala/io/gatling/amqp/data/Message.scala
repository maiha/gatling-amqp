package io.gatling.amqp.data

import com.rabbitmq.client.AMQP.BasicProperties

case class Message(
  properties: BasicProperties,
  payload: Array[Byte]
)
