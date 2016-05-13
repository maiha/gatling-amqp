package io.gatling.amqp.request.builder

import io.gatling.amqp.data._

trait Consuming { this: AmqpRequestBuilder =>
  /**
    *
    * @param queueName
    * @param autoAck  NOT implemented yet to be false (async consume)
    * @return
    */
  def consume(queueName: String, autoAck: Boolean = true): AmqpRequestBuilder =
    consume(AsyncConsumerRequest(queueName, autoAck = autoAck))

  /**
    * Request to consume single message from queue and optionally save it in session. This should be used if you want
    * to make rpc call. This part is for receiving response after publish to make request was send.
    *
    * This call is blocking and waits for next message to be consumed.
    *
    * @param queueName
    * @param autoAck             NOT implemented yet to be false (async consume)
    * @param saveResultToSession if true, consumed message will be saved in session attributes
    *                            under key { @link io.gatling.amqp.infra.AmqpConsumer#LAST_CONSUMED_MESSAGE_KEY}
    * @return
    */
  def consumeSingle(queueName: String, autoAck: Boolean = true, saveResultToSession: Boolean = false): AmqpRequestBuilder =
    consume(ConsumeSingleMessageRequest(queueName, autoAck = autoAck, saveResultToSession = saveResultToSession))

  def consume(req: ConsumeRequest): AmqpRequestBuilder = {
    _request.foreach(_ =>
      throw new RuntimeException(s"sTry to define consume, but previously some action was defined (${_request}). Use separate exec for this action!"))
    _request = Some(req)
    this
  }
}
