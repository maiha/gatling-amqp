package io.gatling.amqp.config

import com.typesafe.config.ConfigFactory

trait AmqpSupport {
  protected lazy val config = AmqpConfig(ConfigFactory.load)
}
