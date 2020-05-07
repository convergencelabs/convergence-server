/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain

import akka.actor.ActorRef

sealed trait DomainMessage {
  val domainFqn: DomainId
}

case class HandshakeRequest(domainFqn: DomainId, clientActor: ActorRef, reconnect: Boolean, reconnectToken: Option[String]) extends DomainMessage
case class AuthenticationRequest(
  domainFqn:      DomainId,
  clientActor:    ActorRef,
  remoteAddress:  String,
  client:         String,
  clientVersion:  String,
  clientMetaData: String,
  credentials:    AuthenticationCredentials) extends DomainMessage

case class ClientDisconnected(domainFqn: DomainId, clientActor: ActorRef) extends DomainMessage

case class DomainStatusRequest(domainFqn: DomainId) extends DomainMessage
case class DomainStatusResponse(connectedClients: Int)

sealed trait AuthenticationCredentials
case class PasswordAuthRequest(username: String, password: String) extends AuthenticationCredentials
case class JwtAuthRequest(jwt: String) extends AuthenticationCredentials
case class ReconnectTokenAuthRequest(token: String) extends AuthenticationCredentials
case class AnonymousAuthRequest(displayName: Option[String]) extends AuthenticationCredentials

sealed trait AuthenticationResponse
case class AuthenticationSuccess(session: DomainUserSessionId, reconnectToken: Option[String]) extends AuthenticationResponse
case object AuthenticationFailure extends AuthenticationResponse

case class AuthenticationError(message: String = "", cause: Throwable) extends Exception(message, cause)
case class UnauthorizedException(message: String = "") extends Exception(message)

case class HandshakeSuccess(
  modelStoreActor:     ActorRef,
  operationStoreActor: ActorRef,
  userService:         ActorRef,
  presenceService:     ActorRef,
  chatLookupService:   ActorRef)

case class HandshakeFailureException(code: String, details: String) extends RuntimeException(details)
