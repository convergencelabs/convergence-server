package com.convergencelabs.server.util

import akka.actor.ActorRef

trait WorkQueue {
  def scheduleWork(work: () => Any)
}

object ActorWorkQueue {
  case class DoWork(work: () => Any)
}

class ActorWorkQueue(actor: ActorRef) extends WorkQueue {
  import ActorWorkQueue._

  def scheduleWork(work: () => Any): Unit = {
    actor ! DoWork(work)
  }

}