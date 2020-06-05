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

package com.convergencelabs.convergence.server.datastore.convergence

import java.time.Duration

import akka.actor.typed.ActorRef
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.security.AuthorizationProfileData

private[convergence] trait AuthenticationActorProtocol {

  sealed trait Message extends CborSerializable

  //
  // Auth
  //
  case class AuthRequest(username: String, password: String, replyTo: ActorRef[AuthResponse]) extends Message

  sealed trait AuthError

  case class AuthenticationFailed() extends AuthError

  case class AuthData(token: String, expiration: Duration)

  case class AuthResponse(data: Either[AuthError, AuthData]) extends CborSerializable


  //
  // Login
  //
  case class LoginRequest(username: String, password: String, replyTo: ActorRef[LoginResponse]) extends Message

  sealed trait LoginError extends CborSerializable

  case class LoginFailed() extends LoginError

  case class LoginResponse(bearerToken: Either[LoginError, String]) extends CborSerializable

  //
  // ValidateResponse
  //
  sealed trait ValidateError

  case class ValidationFailed() extends ValidateError

  case class ValidateResponse(profile: Either[ValidateError, AuthorizationProfileData]) extends CborSerializable


  //
  // ValidateSessionToken
  //
  case class ValidateSessionTokenRequest(token: String, replyTo: ActorRef[ValidateResponse]) extends Message


  //
  // ValidateUserBearerToken
  //
  case class ValidateUserBearerTokenRequest(bearerToken: String, replyTo: ActorRef[ValidateResponse]) extends Message


  //
  // ValidateUserApiKey
  //
  case class ValidateUserApiKeyRequest(apiKey: String, replyTo: ActorRef[ValidateResponse]) extends Message

  //
  // GetSessionTokenExpiration
  //
  case class GetSessionTokenExpirationRequest(token: String, replyTo: ActorRef[GetSessionTokenExpirationResponse]) extends Message

  sealed trait GetSessionTokenExpirationError

  case class TokenNotFoundError() extends GetSessionTokenExpirationError

  case class SessionTokenExpiration(username: String, expiration: Duration)

  case class GetSessionTokenExpirationResponse(expiration: Either[GetSessionTokenExpirationError, SessionTokenExpiration]) extends CborSerializable

  //
  // InvalidateTokenRequest
  //
  case class InvalidateSessionTokenRequest(token: String, replyTo: ActorRef[InvalidateSessionTokenResponse]) extends Message

  sealed trait InvalidateSessionTokenError

  case class InvalidateSessionTokenResponse(response: Either[InvalidateSessionTokenError, Unit]) extends CborSerializable

  //
  // Common Errors
  //

  case class UnknownError() extends AuthError
    with LoginError
    with ValidateError
    with GetSessionTokenExpirationError
    with InvalidateSessionTokenError
}
