package io.gatling.amqp.data

import io.gatling.amqp.infra.AmqpConsumerCorrelation
import io.gatling.core.session._

// data for basicConsume(java.lang.String queue, boolean autoAck, java.lang.String consumerTag, boolean noLocal, boolean exclusive, java.util.Map<java.lang.String,java.lang.Object> arguments, Consumer callback) 
sealed trait ConsumeRequest extends AmqpRequest

case class AsyncConsumerRequest(
                                 requestName: Expression[String],
                                 queue: String,
                                 autoAck: Boolean
                               ) extends ConsumeRequest

case class ConsumeSingleMessageRequest(
                                        requestName: Expression[String],
                                        queueName: String,
                                        autoAck: Boolean = true,

                                        /**
                                          * If set to true, session will contain key {@link io.gatling.amqp.infra.AmqpConsumer#LAST_CONSUMED_MESSAGE_KEY} with value
                                          * of last delivered (consumed) message.
                                          */
                                        saveResultToSession: Boolean = false,

                                        /**
                                          * If you fill in some parameter, AmqpConsumer actor will consume all messages and than route received messages acording
                                          * correlationId parameter to right next action.
                                          *
                                          * NOTE: If you use this parameter on single step, you should not use general {@link AsyncConsumerRequest}, because you will probably miss some message and this request will stuck forever.
                                          */
                                        correlationId: Option[Expression[String]] = None,

                                        /**
                                          * Advanced version of receiving single message. By providing this lambda, you are able to provide/override
                                          * correlation id of all messages to any custom values. You can for example pass here function, which will
                                          * return "PayloadId" from header. Than all received messages will be taken as theirs correlation id was
                                          * actually value contained in PayloadId in header. No mather if received messages contained correlation
                                          * id or not.
                                          *
                                          * Note, conversion function is just single one for all users in given step, thus it is not Expression.
                                          */
                                        customCorrelationIdTransformer: Option[AmqpConsumerCorrelation.ReceivedData => String] = None
                                      ) extends ConsumeRequest
