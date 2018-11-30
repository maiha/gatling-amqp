package io.gatling.amqp.request.builder

import io.gatling.amqp.data._
import io.gatling.core.session.Expression

/*
 * TODO - provide additional methods with signature:
 * - consume(queueName: String, autoAck: Boolean): AmqpRequestBuilder
 */
trait Consuming { this: AmqpRequestBuilder =>
  def consume(req: Expression[ConsumeRequest]): AmqpRequestBuilder = {
    _request = Some(req)
    publishRequestType = Some(false)
    this
  }
}
