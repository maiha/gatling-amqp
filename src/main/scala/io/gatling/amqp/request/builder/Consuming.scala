package io.gatling.amqp.request.builder

import io.gatling.amqp.data._

trait Consuming { this: AmqpRequestBuilder =>
  /**
    *
    * @param queueName
    * @param autoAck  NOT implemented yet to be false (async consume)
    * @param saveResultToSession  if true, consumed message will be saved in session attributes
    *                             under key {@link io.gatling.amqp.infra.AmqpConsumer#LAST_CONSUMED_MESSAGE_KEY}
    * @return
    */
  def consume(queueName: String, autoAck: Boolean = true, saveResultToSession: Boolean = false): AmqpRequestBuilder =
    consume(ConsumeRequest(queueName, autoAck = autoAck, saveResultToSession = saveResultToSession))

  def consume(req: ConsumeRequest): AmqpRequestBuilder = {
    _request = Some(req)
    this
  }
}
