package io.gatling.amqp.data

// data for basicConsume(java.lang.String queue, boolean autoAck, java.lang.String consumerTag, boolean noLocal, boolean exclusive, java.util.Map<java.lang.String,java.lang.Object> arguments, Consumer callback) 
sealed trait ConsumeRequest extends AmqpRequest

case class AsyncConsumerRequest(
                                 queue: String,
                                 autoAck: Boolean
                               ) extends ConsumeRequest

case class ConsumeSingleMessageRequest(
                                        queue: String,
                                        autoAck: Boolean = true,
  /**
    * If set to true, session will contain key {@link io.gatling.amqp.infra.AmqpConsumer#LAST_CONSUMED_MESSAGE_KEY} with value
    * of last delivered (consumed) message.
    */
  saveResultToSession: Boolean = false
                                      ) extends ConsumeRequest