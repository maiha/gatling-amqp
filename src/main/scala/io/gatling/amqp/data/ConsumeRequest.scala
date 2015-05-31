package io.gatling.amqp.data

// data for basicConsume(java.lang.String queue, boolean autoAck, java.lang.String consumerTag, boolean noLocal, boolean exclusive, java.util.Map<java.lang.String,java.lang.Object> arguments, Consumer callback) 
case class ConsumeRequest(
  queue: String,
  autoAck: Boolean
) extends AmqpRequest

object ConsumeRequest {
}
