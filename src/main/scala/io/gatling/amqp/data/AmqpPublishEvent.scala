package io.gatling.amqp.data

trait AmqpPublishEvent {
  val req: PublishRequest
  def onProcess(id: Int): Unit
  def onSuccess(id: Int): Unit
  def onFailure(id: Int, e: Throwable): Unit
}
