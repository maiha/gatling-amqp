package io.gatling.amqp.util

import scala.collection.mutable

class FastBitRequest[A]() {
  private val bit = new mutable.BitSet()           // index as bit
  private val map = mutable.OpenHashMap[Int, A]()  // index -> A
  protected def logName: String = "FastBitRequest"

  def request(n: Int, value: A): Unit = synchronized {
    bit.add(n)
    map.put(n, value)

    debugRequested(n)
  }

  def complete(n: Int, multiple: Boolean, callback: (Int, A) => Unit): Unit = synchronized {
    if (multiple)
      bit.takeWhile(_ <= n).filter(isRequesting).foreach(i => complete(i, callback))
    else
      complete(n, callback)
  }

  private def isRequesting(n: Int): Boolean = bit.contains(n)

  private sealed trait Status
  private case object Requested extends Status
  private case object Completed extends Status
  private val states = mutable.OpenHashMap[Int, Status]()
  private def debugRequested(i: Int): Unit = states.put(i, Requested)
  private def debugCompleted(i: Int): Unit = states.put(i, Completed)
  private def debugStates(): Unit = {
    println(s"RequestCompleted($logName): ${states.size} $states")
  }

  def complete(n: Int, callback: (Int, A) => Unit): Unit = synchronized {
    debugCompleted(n)
//    debugStates()

    callback(n, at(n))
    bit.remove(n)
    map.remove(n)
  }

  private def at(n: Int) = map.getOrElse(n, throw new RuntimeException(s"[BUG] at($n) not found in map"))
}
