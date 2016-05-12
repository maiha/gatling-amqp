package io.gatling.amqp.data

// data for basicConsume(java.lang.String queue, boolean autoAck, java.lang.String consumerTag, boolean noLocal, boolean exclusive, java.util.Map<java.lang.String,java.lang.Object> arguments, Consumer callback) 
case class ConsumeRequest(
  queue: String,
  autoAck: Boolean,
  /**
    * If set to true, session will contain key {@link io.gatling.amqp.infra.AmqpConsumer#LAST_CONSUMED_MESSAGE_KEY} with value
    * of last delivered (consumed) message.
    */
  saveResultToSession: Boolean = false
) extends AmqpRequest
