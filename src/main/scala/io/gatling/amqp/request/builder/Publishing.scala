package io.gatling.amqp.request.builder

import io.gatling.amqp.data.PublishRequest
import io.gatling.core.session.Expression

/* TODO - provide additional methods with signature:
 * - publish(queueName: String, bytes: Array[Byte]): AmqpRequestBuilder
 * - publish(queueName: String, body: String): AmqpRequestBuilder
 */
trait Publishing { this: AmqpRequestBuilder =>
  def publish(req: Expression[PublishRequest]): AmqpRequestBuilder = {
    _request = Some(req)
    publishRequestType = Some(true)
    this
  }
}
