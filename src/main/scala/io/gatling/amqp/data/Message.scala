package io.gatling.amqp.data

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.MessageProperties

case class Message(
  properties: BasicProperties,
  payload: Array[Byte]
)
