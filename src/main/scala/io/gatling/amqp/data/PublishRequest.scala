package io.gatling.amqp.data

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.MessageProperties

case class PublishRequest(
  exchange: Exchange,
  routingKey: String,
  props: BasicProperties,
  bytes: Array[Byte]
) extends AmqpRequest {

  def withProps[A](b : BasicProperties.Builder => A): PublishRequest = {
    val builder = new BasicProperties.Builder()
    b(builder)
    copy(props = builder.build())
  }

  def persistent: PublishRequest = copy(props = MessageProperties.MINIMAL_PERSISTENT_BASIC)
}

object PublishRequest {
  def apply(queueName: String, bytes: Array[Byte]): PublishRequest =
    new PublishRequest(Exchange.Direct, queueName, props(), bytes)

  def apply(queueName: String, body: String): PublishRequest =
    new PublishRequest(Exchange.Direct, queueName, props(), body.getBytes("UTF-8"))

  def apply(exchange: Exchange, routingKey: String, body: String): PublishRequest =
    new PublishRequest(exchange, routingKey, props(), body.getBytes("UTF-8"))

  def apply(exchange: Exchange, routingKey: String, bytes: Array[Byte]): PublishRequest =
    new PublishRequest(exchange, routingKey, props(), bytes)

  def props(): BasicProperties = {
    val builder = new BasicProperties.Builder()
    builder.build()
  }
}
