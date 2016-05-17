package io.gatling.amqp.data

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.MessageProperties
import io.gatling.core.session._

case class PublishRequest(
                           exchange: Exchange,
                           routingKey: String,
                           props: BasicProperties,
                           bytes: Either[Expression[Array[Byte]], Array[Byte]]
) extends AmqpRequest {

  def withProps[A](b : BasicProperties.Builder => A): PublishRequest = {
    val builder = new BasicProperties.Builder()
    b(builder)
    copy(props = builder.build())
  }

  def persistent: PublishRequest = copy(props = MessageProperties.MINIMAL_PERSISTENT_BASIC)
}

object PublishRequest {
  def apply(queueName: String, bytes: Array[Byte]): PublishRequest =
    new PublishRequest(Exchange.Direct, queueName, props(), Right(bytes))

  def apply(queueName: String, bytes: String): PublishRequest =
    apply(queueName, Right(bytes))

  def apply(queueName: String, bodyStr: Either[Expression[String], String]): PublishRequest = {
    val b = bodyStr match {
      case Left(l) => Left(l.map(_.getBytes("UTF-8")))
      case Right(r) => Right(r.getBytes("UTF-8"))
    }
    new PublishRequest(Exchange.Direct, queueName, props(), b)
  }

  //http://stackoverflow.com/questions/3307427/scala-double-definition-2-methods-have-the-same-type-erasure
  def apply[X: ClassManifest](queueName: String, body: Either[Expression[Array[Byte]], Array[Byte]]): PublishRequest = {
    new PublishRequest(Exchange.Direct, queueName, props(), body)
  }

  def apply(exchange: Exchange, routingKey: String, body: String): PublishRequest =
    new PublishRequest(exchange, routingKey, props(), Right(body.getBytes("UTF-8")))

  def props(): BasicProperties = {
    val builder = new BasicProperties.Builder()
    builder.build()
  }
}
