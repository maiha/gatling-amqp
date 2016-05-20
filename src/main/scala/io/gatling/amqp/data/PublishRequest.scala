package io.gatling.amqp.data

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.MessageProperties
import io.gatling.core.Predef._
import io.gatling.core.session._

case class PublishRequest(
                           exchange: Expression[String],
                           routingKey: Expression[String],
                           props: Expression[BasicProperties],
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
  def apply(exchangeName: Expression[String], bytes: Array[Byte]): PublishRequest =
    new PublishRequest(exchangeName, "", props(), Right(bytes))

  def apply(exchangeName: Expression[String], bytes: String): PublishRequest =
    apply(exchangeName, Right(bytes))

  def apply(exchangeName: Expression[String], bodyStr: Either[Expression[String], String], properties: Expression[BasicProperties] = props()): PublishRequest = {
    val b = bodyStr match {
      case Left(l) => Left(l.map(_.getBytes("UTF-8")))
      case Right(r) => Right(r.getBytes("UTF-8"))
    }
    new PublishRequest(exchangeName, "", properties, b)
  }

  //http://stackoverflow.com/questions/3307427/scala-double-definition-2-methods-have-the-same-type-erasure
  def apply[X: ClassManifest](queueName: String, body: Either[Expression[Array[Byte]], Array[Byte]]): PublishRequest = {
    new PublishRequest(queueName, "", props(), body)
  }

  def apply(exchangeName: Expression[String], routingKey: Expression[String], body: Either[Expression[Array[Byte]], Array[Byte]]): PublishRequest =
    new PublishRequest(exchangeName, routingKey, props(), body)

  def props(): BasicProperties = {
    new BasicProperties.Builder().build()
  }
}
