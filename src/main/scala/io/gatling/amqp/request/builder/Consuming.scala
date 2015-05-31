package io.gatling.amqp.request.builder

import io.gatling.amqp.data._

trait Consuming { this: AmqpRequestBuilder =>
  def consume(queueName: String, autoAck: Boolean): AmqpRequestBuilder =
    consume(ConsumeRequest(queueName, autoAck = autoAck))

  def consume(req: ConsumeRequest): AmqpRequestBuilder = {
    _request = Some(req)
    this
  }
}
