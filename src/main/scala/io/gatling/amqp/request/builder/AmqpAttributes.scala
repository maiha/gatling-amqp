package io.gatling.amqp.request.builder

import io.gatling.amqp.data.AmqpRequest
import io.gatling.core.session.Expression

case class AmqpAttributes[T <: AmqpRequest](requestName: Expression[String],
                                            payload: Expression[T]
                                           )
