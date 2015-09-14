package com.convergencelabs.server.frontend.realtime

import com.convergencelabs.server.frontend.realtime.ws.WebSocketServer
import com.convergencelabs.server.domain.DomainFqn
import akka.actor.ActorSystem
import akka.actor.ActorRef
import com.convergencelabs.server.ProtocolConfiguration

object ConvergenceRealtimeFrontend extends App {
}

class ConvergenceRealtimeFrontend(private[this] val system: ActorSystem) {
  private val protoConfig = ProtocolConfiguration(5000L)
  
  private[this] val connectionHandler = new SocketConnectionHandler()
  
  private[this] val connectionManager = system.actorOf(ConnectionManagerActor.props(protoConfig), "connectionManager")
  
  connectionHandler.addListener((domainFqn: DomainFqn, socket: ConvergenceServerSocket) => {
    connectionManager.tell(NewSocketEvent(domainFqn, socket), ActorRef.noSender)
  })

  private[this] val server = new WebSocketServer(8080, 65535, connectionHandler)
  
  def start(): Unit = {
    server.start()
  }
  
  def stop(): Unit = {
    server.stop()
  }
}