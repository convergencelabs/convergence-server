package com.convergencelabs.server

import akka.actor.ActorRef
import org.json4s.JsonAST.JValue
package object domain {
  case class DomainFqn(namespace: String, domainId: String)
  
  case class HandshakeRequest(domainFqn: DomainFqn, clientActor: ActorRef, reconnect: Boolean, reconnectToken: Option[String])
  
  sealed trait HandshakeResponse
  case class HandshakeSuccess(sessionId: String, reconnectToken: String, domainActor: ActorRef, modelManager: ActorRef) extends HandshakeResponse
  case class HandshakeFailure(reason: String, retry: Boolean) extends HandshakeResponse
  
  case class ClientDisconnected(sessionId: String, clientActor: ActorRef)
  case class DomainShutdownRequest(domainFqn: DomainFqn)
}