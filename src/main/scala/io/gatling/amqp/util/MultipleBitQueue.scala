package io.gatling.amqp.util

import scala.collection.mutable

class MultipleBitQueue[A]() {
  private val bit = new mutable.BitSet()           // index as bit
  private val map = mutable.OpenHashMap[Int, A]()  // index -> A

  def publish(n: Int, value: A): Unit = synchronized {
    bit.add(n)
    map.put(n, value)

//    debugRequested(n)
//    debugStates()
  }

  def consume(n: Int, multiple: Boolean): Unit = synchronized {
    if (multiple)
      bit.takeWhile(_ <= n).filter(contains).foreach(consume)
    else
      consume(n)
  }

  def consume(n: Int): Unit = synchronized {
//    debugCompleted(n)
//    debugStates()
    bit.remove(n)
    map.remove(n)
  }

  protected val contains = bit.contains _
  protected def get(n: Int): A = map.getOrElse(n, throw new RuntimeException(s"[BUG] at($n) not found in $map, $bit"))

  private sealed trait Status
  private case object Requested extends Status
  private case object Completed extends Status
  private val states = mutable.OpenHashMap[Int, Status]()
  private def debugRequested(i: Int): Unit = states.put(i, Requested)
  private def debugCompleted(i: Int): Unit = states.put(i, Completed)
  private def debugStates(): Unit = {
    println(s"RequestCompleted: ${states.size} $states")
  }
}
