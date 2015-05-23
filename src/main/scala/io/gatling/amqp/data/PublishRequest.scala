package io.gatling.amqp.data

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.MessageProperties

case class PublishRequest(
  exchange: Exchange,
  routingKey: String,
  properties: BasicProperties,
  payload: Array[Byte],
  waitAck: Boolean = false,
  count: Int = 1
) extends AmqpRequest {

  def withProps[A](b : BasicProperties.Builder => A): PublishRequest = {
    val builder = new BasicProperties.Builder()
    b(builder)
    copy(properties = builder.build())
  }

  def persistent    : PublishRequest = copy(properties = MessageProperties.MINIMAL_PERSISTENT_BASIC)
  def confirm       : PublishRequest = copy(waitAck = true)
  def repeat(n: Int): PublishRequest = copy(count = n)
}

object PublishRequest {
  def apply(queueName: String, bytes: Array[Byte]): PublishRequest =
    new PublishRequest(Exchange.Direct, queueName, props(), bytes)

  def apply(queueName: String, payload: String): PublishRequest =
    new PublishRequest(Exchange.Direct, queueName, props(), payload.getBytes("UTF-8"))

  def apply(exchange: Exchange, routingKey: String, payload: String): PublishRequest =
    new PublishRequest(exchange, routingKey, props(), payload.getBytes("UTF-8"))

  def apply(exchange: Exchange, routingKey: String, bytes: Array[Byte]): PublishRequest =
    new PublishRequest(exchange, "", props(), bytes)

  def props(): BasicProperties = {
    val builder = new BasicProperties.Builder()
    builder.build()
  }
}
