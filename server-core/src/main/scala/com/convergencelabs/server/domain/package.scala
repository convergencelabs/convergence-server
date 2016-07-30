package com.convergencelabs.server

import com.convergencelabs.server.domain.DomainFqn

import akka.actor.ActorRef
import com.convergencelabs.server.domain.model.SessionKey

package object domain {

  case class HandshakeRequest(domainFqn: DomainFqn, clientActor: ActorRef, reconnect: Boolean, reconnectToken: Option[String])

  sealed trait HandshakeResponse
  case class HandshakeSuccess(
    domainActor: ActorRef,
    modelManager: ActorRef,
    userService: ActorRef,
    activityService: ActorRef,
    presenceService: ActorRef) extends HandshakeResponse
  case class HandshakeFailure(code: String, details: String) extends HandshakeResponse

  case class ClientDisconnected(sessionId: String)
  case class DomainShutdownRequest(domainFqn: DomainFqn)

  sealed trait AuthenticationRequest
  case class PasswordAuthRequest(username: String, password: String) extends AuthenticationRequest
  case class TokenAuthRequest(jwt: String) extends AuthenticationRequest

  sealed trait AuthenticationResponse
  case class AuthenticationSuccess(username: String, sk: SessionKey) extends AuthenticationResponse
  case object AuthenticationFailure extends AuthenticationResponse
  case object AuthenticationError extends AuthenticationResponse
}
