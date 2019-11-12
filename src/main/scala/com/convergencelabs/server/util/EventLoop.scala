/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.util

import akka.actor.ActorRef

trait EventLoop {
  def schedule(task: => Unit)
}

object ActorBackedEventLoop {
  case class TaskScheduled(task: () => Unit)
}

class ActorBackedEventLoop(actor: ActorRef) extends EventLoop {
  import ActorBackedEventLoop._

  def schedule(task: => Unit): Unit = {
    actor ! TaskScheduled((() => task))
  }
}