package io.gatling.amqp.data

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.MessageProperties
import io.gatling.core.Predef._
import io.gatling.core.session._

sealed trait PublishRequest extends AmqpRequest {
  def exchange: Expression[String]

  def routingKey: Expression[String]

  def props: Expression[BasicProperties]

  def bytes: Either[Expression[Array[Byte]], Array[Byte]]
}

/**
  * Partly blocking publish request. It will also save correlationId in session under
  * [[io.gatling.amqp.infra.AmqpPublisher.LAST_PUBLISHED_MESSAGE_CORRELATIONID_KEY]] key.
  *
  * @param exchange
  * @param routingKey
  * @param props
  * @param bytes
  */
case class RpcCallRequest(
                           requestName: Expression[String],
                           exchange: Expression[String],
                           routingKey: Expression[String],
                           props: Expression[BasicProperties],
                           bytes: Either[Expression[Array[Byte]], Array[Byte]]
                         ) extends PublishRequest

object RpcCallRequest {
  def apply(requestName: Expression[String],
            exchange: Expression[String],
            props: Expression[BasicProperties],
            bodyStr: Either[Expression[String], String]): RpcCallRequest = {
    val b = bodyStr match {
      case Left(l) => Left(l.map(_.getBytes("UTF-8")))
      case Right(r) => Right(r.getBytes("UTF-8"))
    }
    RpcCallRequest(requestName, exchange, "", props, b)
  }
}

case class PublishRequestAsync(
                                requestName: Expression[String],
                                exchange: Expression[String],
                                routingKey: Expression[String],
                                props: Expression[BasicProperties],
                                bytes: Either[Expression[Array[Byte]], Array[Byte]]
                              ) extends PublishRequest {

  def withProps[A](b: BasicProperties.Builder => A): PublishRequestAsync = {
    val builder = new BasicProperties.Builder()
    b(builder)
    copy(props = builder.build())
  }

  def persistent: PublishRequestAsync = copy(props = MessageProperties.MINIMAL_PERSISTENT_BASIC)
}

object PublishRequestAsync {
  def apply(requestName: Expression[String], exchangeName: Expression[String], bytes: Array[Byte]): PublishRequestAsync =
    new PublishRequestAsync(requestName, exchangeName, "", props(), Right(bytes))

  def apply(requestName: Expression[String], exchangeName: Expression[String], bytes: String): PublishRequestAsync =
    apply(requestName, exchangeName, Right(bytes))

  def apply(requestName: Expression[String], exchangeName: Expression[String], bodyStr: Either[Expression[String], String], properties: Expression[BasicProperties] = props()): PublishRequestAsync = {
    val b = bodyStr match {
      case Left(l) => Left(l.map(_.getBytes("UTF-8")))
      case Right(r) => Right(r.getBytes("UTF-8"))
    }
    new PublishRequestAsync(requestName, exchangeName, "", properties, b)
  }

  //http://stackoverflow.com/questions/3307427/scala-double-definition-2-methods-have-the-same-type-erasure
  def apply[X: ClassManifest](requestName: Expression[String], queueName: String, body: Either[Expression[Array[Byte]], Array[Byte]]): PublishRequestAsync = {
    new PublishRequestAsync(requestName, queueName, "", props(), body)
  }

  def apply(requestName: Expression[String], exchangeName: Expression[String], routingKey: Expression[String], body: Either[Expression[Array[Byte]], Array[Byte]]): PublishRequestAsync =
    new PublishRequestAsync(requestName, exchangeName, routingKey, props(), body)

  def props(): BasicProperties = {
    new BasicProperties.Builder().build()
  }
}
