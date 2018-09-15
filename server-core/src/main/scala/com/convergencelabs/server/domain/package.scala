package com.convergencelabs.server.domain

import akka.actor.ActorRef
import com.convergencelabs.server.domain.model.SessionKey

sealed trait DomainMessage {
  val domainFqn: DomainFqn
}

case class HandshakeRequest(domainFqn: DomainFqn, clientActor: ActorRef, reconnect: Boolean, reconnectToken: Option[String]) extends DomainMessage
case class AuthenticationRequest(
  domainFqn:      DomainFqn,
  clientActor:    ActorRef,
  remoteAddress:  String,
  client:         String,
  clientVersion:  String,
  clientMetaData: String,
  credentials:    AuthetncationCredentials) extends DomainMessage

case class ClientDisconnected(domainFqn: DomainFqn, sessionId: String) extends DomainMessage

sealed trait AuthetncationCredentials
case class PasswordAuthRequest(username: String, password: String) extends AuthetncationCredentials
case class JwtAuthRequest(jwt: String) extends AuthetncationCredentials
case class ReconnectTokenAuthRequest(token: String) extends AuthetncationCredentials
case class AnonymousAuthRequest(displayName: Option[String]) extends AuthetncationCredentials

sealed trait AuthenticationResponse
case class AuthenticationSuccess(username: String, sk: SessionKey, reconnectToken: String) extends AuthenticationResponse
case object AuthenticationFailure extends AuthenticationResponse
case object AuthenticationError extends AuthenticationResponse

case class UnauthorizedException(message: String = "") extends Exception(message)

case class HandshakeSuccess(
  domainActor:         ActorRef,
  modelQueryActor:     ActorRef,
  modelStoreActor:     ActorRef,
  operationStoreActor: ActorRef,
  userService:         ActorRef,
  presenceService:     ActorRef,
  chatLookupService:   ActorRef)

case class HandshakeFailureException(code: String, details: String) extends RuntimeException(details)
