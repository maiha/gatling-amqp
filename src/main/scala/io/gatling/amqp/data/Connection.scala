package io.gatling.amqp.data

case class Connection(
  host    : String   = "localhost",
  port    : Int      = 5672,
  vhost   : String   = "/",
  user    : String   = "guest",
  password: String   = "guest",
  poolSize: Int      = 3,
  confirm : Boolean  = false
) {

  def validate(): Unit = {
    require(host.nonEmpty    ,  "AMQP host is not set")
    require(port > 0         , s"AMQP port is invalid: $port")
    require(vhost.nonEmpty   ,  "AMQP vhost is not set")
    require(user.nonEmpty    ,  "AMQP user is not set")
    require(password.nonEmpty,  "AMQP password is not set")
    require(poolSize > 0     , s"AMQP connection poolSize is invalid: $poolSize")
  }
}
