package io.gatling.amqp.data

case class Exchange(name: String, tpe: String) {
  def validate(): Unit = {
    require(tpe.nonEmpty, "AMQP exchange.type is not set")
  }
} 

object Exchange {
  object Direct extends Exchange("", "direct")
}
