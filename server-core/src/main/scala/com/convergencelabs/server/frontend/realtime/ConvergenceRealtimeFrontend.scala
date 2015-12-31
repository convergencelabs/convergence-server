package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.frontend.realtime.ws.WebSocketServer

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Inbox
import grizzled.slf4j.Logging

class ConvergenceRealtimeFrontend(
    private[this] val system: ActorSystem,
    private[this] val websocketPort: Int) extends Logging {

  // FIXME this object is nonsensical.  It's all over the place.  I don't know
  // if this is the right place for this.
  private val protoConfig = ProtocolConfiguration(Duration.create(5, TimeUnit.SECONDS))

  private[this] val connectionHandler = new SocketConnectionHandler()
  private[this] val inbox = Inbox.create(system)
  private[this] val connectionManager = system.actorOf(ConnectionManagerActor.props(inbox.getRef(), protoConfig), "connectionManager")

  connectionHandler.addListener((domainFqn: DomainFqn, socket: ConvergenceServerSocket) => {
    connectionManager.tell(NewSocketEvent(domainFqn, socket), ActorRef.noSender)
  })

  // FIXME.  Not sure this is the right size.
  private[this] val server = new WebSocketServer(websocketPort, 65535, connectionHandler)

  def start(): Unit = {
    logger.info(s"Realtime Front End starting up on port $websocketPort.")
    val timeout = FiniteDuration(5, TimeUnit.SECONDS)
    inbox.receive(timeout) match {
      case StartUpComplete => {
        server.start()
        logger.info(s"Realtime Front End started up on port $websocketPort.")
      }
    }
  }

  def stop(): Unit = {
    server.stop()
  }
}
