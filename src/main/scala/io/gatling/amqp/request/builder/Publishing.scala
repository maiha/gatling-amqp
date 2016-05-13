package io.gatling.amqp.request.builder

import io.gatling.amqp.data._

trait Publishing { this: AmqpRequestBuilder =>
  def publish(queueName: String, bytes: Array[Byte]): AmqpRequestBuilder =
    publish(PublishRequest(queueName, bytes = bytes))

  def publish(queueName: String, body: String): AmqpRequestBuilder =
    publish(PublishRequest(queueName, body = body))

  def publish(req: PublishRequest): AmqpRequestBuilder = {
    _request.foreach(_ =>
      throw new RuntimeException(s"sTry to define consume, but previously some action was defined (${_request}). Use separate exec for this action!"))
    _request = Some(req)
    this
  }
}
