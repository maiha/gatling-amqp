package io.gatling.amqp.request.builder

import io.gatling.amqp.data._

trait Consuming { this: AmqpRequestBuilder =>
  /**
    *
    * @param queueName
    * @param autoAck  should be true for now, because consumeAsync method in AmqpConsumer class is not implemented
    * @return
    */
  def consume(queueName: String, autoAck: Boolean): AmqpRequestBuilder =
    consume(ConsumeRequest(queueName, autoAck = autoAck))

  def consume(req: ConsumeRequest): AmqpRequestBuilder = {
    _request = Some(req)
    this
  }
}
