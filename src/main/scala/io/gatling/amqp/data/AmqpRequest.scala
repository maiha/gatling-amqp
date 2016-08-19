package io.gatling.amqp.data

import io.gatling.core.session.Expression

/**
 * Marker trait for AMQP Requests
 */
trait AmqpRequest {
  val requestName: Expression[String]
}
