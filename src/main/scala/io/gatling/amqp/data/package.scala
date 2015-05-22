package io.gatling.amqp

import scala.collection.mutable.HashMap

package object data {
  type Arguments = HashMap[String, Object]

  def DefaultArguments: Arguments = HashMap[String, Object]()
}
