package io.gatling.amqp

package object data {
  type Arguments = Map[String, Object]

  def DefaultArguments: Arguments = Map[String, Object]()
}
