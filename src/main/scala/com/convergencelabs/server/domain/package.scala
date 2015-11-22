package com.convergencelabs.server

import akka.actor.ActorRef
import org.json4s.JsonAST.JValue
import java.time.Duration

package object domain {

  case class Domain(
    id: String,
    domainFqn: DomainFqn,
    displayName: String,
    dbUsername: String,
    dbPassword: String)

  case class DomainFqn(namespace: String, domainId: String)
  case class TokenPublicKey(id: String, name: String, description: String, keyDate: Long, key: String, enabled: Boolean)
  case class TokenKeyPair(publicKey: String, privateKey: String)

  case class ModelSnapshotConfig(
      snapshotsEnabled: Boolean,
      triggerByVersion: Boolean,
      limitedByVersion: Boolean,
      minimumVersionInterval: Long,
      maximumVersionInterval: Long,
      triggerByTime: Boolean,
      limitedByTime: Boolean,
      minimumTimeInterval: Duration,
      maximumTimeInterval: Duration) {
  }

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