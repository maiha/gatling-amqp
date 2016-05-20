package io.gatling.amqp.request.builder

import com.rabbitmq.client.AMQP.BasicProperties
import io.gatling.amqp.data._
import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import io.gatling.core.util.TimeHelper

trait Publishing {
  this: AmqpRequestBuilder =>
  def publish(exchangeName: Expression[String], body: Either[Expression[String], String], replyToProperty: Option[String] = None): AmqpRequestBuilder = {
    val bb = new BasicProperties.Builder() //.headers(Map(keyValue)) // keyValue: (String, String), // import scala.collection.JavaConversions._
    replyToProperty.map(bb.replyTo(_))
    publish(PublishRequest(exchangeName, body, bb.build()))
  }

  /**
    * Similar to [[publish()]] call, but will include also correlation id according session (session.userId) and time.
    *
    * correlation id will be part of session upon publish event
    *
    * @return
    */
  def rpcCall(
               exchangeName: Expression[String],
               body: Either[Expression[String], String],
               replyToProperty: Option[String] = None,
               corrId: Expression[String] = session => {
                 session.userId + "-" + TimeHelper.nowMillis
               }): AmqpRequestBuilder = {
    val propExpression: Expression[BasicProperties] = session => {
      val bb = new BasicProperties.Builder() //.headers(Map(keyValue)) // keyValue: (String, String), // import scala.collection.JavaConversions._
      replyToProperty.map(bb.replyTo(_))
      bb.correlationId(corrId.apply(session).get)
      bb.build()
    }
    publish(PublishRequest(exchangeName, body, propExpression))
  }

  def publishToQueue(queueName: Expression[String], msgBody: Either[Expression[Array[Byte]], Array[Byte]], properties: Expression[BasicProperties]): AmqpRequestBuilder = {
    publish(PublishRequest("", queueName, properties, msgBody))
  }

  def publish(req: PublishRequest): AmqpRequestBuilder = {
    _request.foreach(_ =>
      throw new RuntimeException(s"sTry to define consume, but previously some action was defined (${_request}). Use separate exec for this action!"))
    _request = Some(req)
    this
  }
}
