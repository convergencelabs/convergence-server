package com.convergencelabs.server.util

import akka.actor.ActorRef

trait WorkQueue {
  def scheduleWork(work: Unit => Any)
}

object ActorWorkQueue {
  case class DoWork(work: Unit => Any)
}

class ActorWorkQueue(actor: ActorRef) extends WorkQueue {
  import ActorWorkQueue._

  def scheduleWork(work: Unit => Any): Unit = {
    actor ! DoWork(work)
  }

}