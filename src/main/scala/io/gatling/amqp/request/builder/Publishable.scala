package io.gatling.amqp.request.builder

import io.gatling.amqp.data._

trait Publishable { this: AmqpRequestBuilder =>
  def publish(queueName: String, bytes: Array[Byte]): AmqpRequestBuilder =
    publish(PublishRequest(queueName, bytes = bytes))

  def publish(req: PublishRequest): AmqpRequestBuilder = {
    requests += req
    this
  }
}
