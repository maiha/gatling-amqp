package io.gatling.amqp.request.builder

import io.gatling.amqp.data._
import io.gatling.core.session.Expression

trait Publishing { this: AmqpRequestBuilder =>
  def publish(queueName: String, body: Either[Expression[String], String]): AmqpRequestBuilder =
    publish(PublishRequest(queueName, bodyStr = body))

  def publish(req: PublishRequest): AmqpRequestBuilder = {
    _request.foreach(_ =>
      throw new RuntimeException(s"sTry to define consume, but previously some action was defined (${_request}). Use separate exec for this action!"))
    _request = Some(req)
    this
  }
}
