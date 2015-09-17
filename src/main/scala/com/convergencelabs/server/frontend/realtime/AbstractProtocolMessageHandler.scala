package com.convergencelabs.server.frontend.realtime

import akka.actor.ActorRef

abstract class AbstractProtocolMessageHandler(protected val clientActor: ActorRef) {
  
  def handleMessage: PartialFunction[ProtocolMessageEvent,Unit] = {
    case m: MessageReceived => onMessageReceived(m)
    case r: RequestReceived => onRequestReceived(r)
  }
  
  def onRequestReceived(event: RequestReceived): Unit
  
  def onMessageReceived(event: MessageReceived): Unit 
}