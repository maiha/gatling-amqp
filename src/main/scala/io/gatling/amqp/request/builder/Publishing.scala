package io.gatling.amqp.request.builder

import com.fasterxml.jackson.annotation.ObjectIdGenerators.UUIDGenerator
import com.rabbitmq.client.AMQP.BasicProperties
import io.gatling.amqp.data._
import io.gatling.core.Predef._
import io.gatling.core.session.Expression

import scala.collection.JavaConversions._

trait Publishing {
  this: AmqpRequestBuilder =>

  def publish(exchangeName: Expression[String], body: Either[Expression[String], String], replyToProperty: Option[String] = None, headers: Map[String, AnyRef] = Map.empty): AmqpRequestBuilder = {
    val bb = new BasicProperties.Builder()
    replyToProperty.map(bb.replyTo(_))
    bb.headers(headers)
    publish(PublishRequestAsync(this.requestName, exchangeName, body, bb.build()))
  }

  val generator: UUIDGenerator = new UUIDGenerator()

  /**
    * Similar to [[Publishing.publish()]] call, but will include also correlation id according session (session.userId) and time.
    *
    * correlation id will be part of session upon publish event. You can also override correlation id if you want. It is
    * last parameter.
    *
    * Note, that this call is partially blocking (more than normal publish), as it have to do its way to publisher, update
    * session with correlationId and than resume next action and do actual publishing. Correlation id is saved under
    * [[io.gatling.amqp.infra.AmqpPublisher.LAST_PUBLISHED_MESSAGE_CORRELATIONID_KEY]] key.
    *
    * @return
    */
  def publishRpcCall(
                      exchangeName: Expression[String],
                      body: Either[Expression[String], String],
                      replyToProperty: Option[String] = None,
                      customHeaders: Map[String, AnyRef] = Map.empty,
                      corrId: Expression[String] = session => {
                        //session.userId + "-" + TimeHelper.nowMillis
                        // TODO use session user id and timestamp instead of UUID!
                        generator.generateId().toString
               }): AmqpRequestBuilder = {
    val propExpression: Expression[BasicProperties] = session => {
      val bb = new BasicProperties.Builder() //.headers(Map(keyValue)) // keyValue: (String, String), // import scala.collection.JavaConversions._
      bb.headers(customHeaders)
      replyToProperty.map(bb.replyTo(_))
      bb.correlationId(corrId.apply(session).get)
      bb.build()
    }
    publish(RpcCallRequest(this.requestName, exchangeName, propExpression, body))
  }

  def publishToQueue(queueName: Expression[String], msgBody: Either[Expression[Array[Byte]], Array[Byte]], properties: Expression[BasicProperties]): AmqpRequestBuilder = {
    publish(PublishRequestAsync(this.requestName, "", queueName, properties, msgBody))
  }

  def publish(req: PublishRequest): AmqpRequestBuilder = {
    _request.foreach(_ =>
      throw new RuntimeException(s"sTry to define consume, but previously some action was defined (${_request}). Use separate exec for this action!"))
    _request = Some(req)
    this
  }
}
