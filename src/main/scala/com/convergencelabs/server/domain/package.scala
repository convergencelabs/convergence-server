package com.convergencelabs.server

import akka.actor.ActorRef
import org.json4s.JsonAST.JValue
package object domain {
  case class DomainFqn(namespace: String, domainId: String)
  
  case class HandshakeRequest(domainFqn: DomainFqn, sessionId: String, clientActor: ActorRef)
  case class HandshakeSuccess(domainActor: ActorRef)
  case class HandshakeFailure(reason: String, retry: Boolean)
  
  case class ClientDisconnected(sessionId: String, clientActor: ActorRef)

  case class DomainShutdownRequest(domainFqn: DomainFqn)
}