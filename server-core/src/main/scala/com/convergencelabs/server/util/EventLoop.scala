package com.convergencelabs.server.util

import akka.actor.ActorRef

trait EventLoop {
  def schedule(work: => Unit)
}

object ActorBackedEventLoop {
  case class DoWork(work: () => Unit)
}

class ActorBackedEventLoop(actor: ActorRef) extends EventLoop {
  import ActorBackedEventLoop._

  def schedule(work: => Unit): Unit = {
    actor ! DoWork((() => work))
  }
}