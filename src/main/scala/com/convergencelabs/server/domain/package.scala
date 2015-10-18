package com.convergencelabs.server

import akka.actor.ActorRef
import org.json4s.JsonAST.JValue

package object domain {
  case class DomainFqn(namespace: String, domainId: String)
  
  case class HandshakeRequest(domainFqn: DomainFqn, clientActor: ActorRef, reconnect: Boolean, reconnectToken: Option[String])
  
  sealed trait HandshakeResponse
  case class HandshakeSuccess(sessionId: String, reconnectToken: String, domainActor: ActorRef, modelManager: ActorRef) extends HandshakeResponse
  case class HandshakeFailure(code: String, details: String) extends HandshakeResponse
  
  case class ClientDisconnected(sessionId: String)
  case class DomainShutdownRequest(domainFqn: DomainFqn)
  
  sealed trait AuthenticationRequest
  case class PasswordAuthRequest(username: String, password: String) extends AuthenticationRequest
  case class TokenAuthRequest(jwt: String) extends AuthenticationRequest

  sealed trait AuthenticationResponse
  case class AuthenticationSuccess(username: String) extends AuthenticationResponse
  case object AuthenticationFailure extends AuthenticationResponse
}