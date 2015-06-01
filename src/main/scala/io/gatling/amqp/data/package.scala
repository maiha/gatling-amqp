package io.gatling.amqp

import scala.collection.mutable

package object data {
  type Arguments = mutable.HashMap[String, Object]

  def DefaultArguments: Arguments = mutable.HashMap[String, Object]()
}
