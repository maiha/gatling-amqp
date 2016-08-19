package io.gatling.amqp.request.builder

import io.gatling.amqp.data._
import io.gatling.amqp.infra.{AmqpConsumerCorrelation, AmqpPublisher}
import io.gatling.core.Predef._
import io.gatling.core.session.Expression

trait Consuming { this: AmqpRequestBuilder =>
  /**
    *
    * @param queueName
    * @param autoAck  NOT implemented yet to be false (async consume)
    * @return
    */
  def consume(queueName: String, autoAck: Boolean = true): AmqpRequestBuilder =
    consume(AsyncConsumerRequest(this.requestName, queueName, autoAck = autoAck))

  /**
    * Request to consume single message from queue and optionally save it in session. This should be used if you want
    * to make rpc call (you need to set correlationId, or you will get possible responses which does not belong to your
    * requests). This part is for receiving response after publish to make request was send.
    *
    * This call is blocking and waits for next message to be consumed.
    *
    * @param queueName
    * @param autoAck                        NOT implemented yet to be false (async consume)
    * @param saveResultToSession            if true, consumed message will be saved in session attributes
    *                                       under key { @link io.gatling.amqp.infra.AmqpConsumer#LAST_CONSUMED_MESSAGE_KEY}
    * @param customCorrelationIdTransformer transformer of received message to correlation id, which will be used instead
    *                                       actual correlation id. By providing some value here, you can match received
    *                                       message by anything needed (not only by correlation id)
    * @return
    */
  def consumeSingle(queueName: String,
                    autoAck: Boolean = true,
                    saveResultToSession: Boolean = false,
                    correlationId: Expression[String] = null,
                    customCorrelationIdTransformer: AmqpConsumerCorrelation.ReceivedData => String = null
                   ): AmqpRequestBuilder =
    consume(ConsumeSingleMessageRequest(this.requestName,
      queueName,
      autoAck = autoAck,
      saveResultToSession = saveResultToSession,
      correlationId = Option(correlationId),
      customCorrelationIdTransformer = Option(customCorrelationIdTransformer)
    ))

  /**
    * Counterpart for [[Publishing.publishRpcCall()]]. This request will automatically use correlation id defined in session under
    * [AmqpPublisher.LAST_PUBLISHED_MESSAGE_CORRELATIONID_KEY].
    *
    * Note: If you want to use custom correlation id key, you can use [[consumeSingle()]] method with last parameter filled in.
    *
    * @param queueName
    * @param autoAck
    * @param saveResultToSession
    * @return
    */
  def consumeRpcResponse(queueName: String, autoAck: Boolean = true, saveResultToSession: Boolean = true): AmqpRequestBuilder =
    consume(ConsumeSingleMessageRequest(
      this.requestName,
      queueName,
      autoAck,
      saveResultToSession,
      //correlationId = Some(s => "${" + AmqpPublisher.LAST_PUBLISHED_MESSAGE_CORRELATIONID_KEY + "}")
      correlationId = Some(s => s(AmqpPublisher.LAST_PUBLISHED_MESSAGE_CORRELATIONID_KEY).as[String])
    ))

  def consume(req: ConsumeRequest): AmqpRequestBuilder = {
    _request.foreach(_ =>
      throw new RuntimeException(s"sTry to define consume, but previously some action was defined (${_request}). Use separate exec for this action!"))
    _request = Some(req)
    this
  }
}
