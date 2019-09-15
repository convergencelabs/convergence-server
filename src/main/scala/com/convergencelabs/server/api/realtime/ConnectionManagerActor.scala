package com.convergencelabs.server.api.realtime

import scala.language.postfixOps

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props

object ConnectionManagerActor {
  def props(): Props = Props(new ConnectionManagerActor())
}

class ConnectionManagerActor()
  extends Actor with ActorLogging {

  def receive: Receive = {
    case msg: Any => unhandled(msg)
  }
}
