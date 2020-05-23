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

import java.time.{Duration, Instant}

import akka.actor.{ActorLogging, Props}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.datastore.convergence.RoleStore.UserRoles
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.security.AuthorizationProfileData
import com.convergencelabs.convergence.server.util.RandomStringGenerator

import scala.util.{Success, Try}


class AuthenticationActor private[datastore](private[this] val dbProvider: DatabaseProvider)
  extends StoreActor with ActorLogging {

  import AuthenticationActor._

  private[this] val userStore = new UserStore(dbProvider)
  private[this] val userApiKeyStore = new UserApiKeyStore(dbProvider)
  private[this] val roleStore = new RoleStore(dbProvider)
  private[this] val configStore = new ConfigStore(dbProvider)
  private[this] val userSessionTokenStore = new UserSessionTokenStore(dbProvider)
  private[this] val sessionTokenGenerator = new RandomStringGenerator(32)

  def receive: Receive = {
    case authRequest: AuthRequest =>
      authenticateUser(authRequest)
    case loginRequest: LoginRequest =>
      onLogin(loginRequest)
    case validateRequest: ValidateSessionTokenRequest =>
      onValidateSessionToken(validateRequest)
    case validateUserBearerTokenRequest: ValidateUserBearerTokenRequest =>
      onValidateBearerToken(validateUserBearerTokenRequest)
    case validateUserApiKey: ValidateUserApiKeyRequest =>
      onValidateApiKey(validateUserApiKey)
    case tokenExpirationRequest: GetSessionTokenExpirationRequest =>
      onGetSessionTokenExpiration(tokenExpirationRequest)
    case invalidateTokenRequest: InvalidateTokenRequest =>
      onInvalidateToken(invalidateTokenRequest)
    case message: Any =>
      unhandled(message)
  }

  private[this] def authenticateUser(authRequest: AuthRequest): Unit = {
    val response = for {
      timeout <- configStore.getSessionTimeout()
      valid <- userStore.validateCredentials(authRequest.username, authRequest.password)
      resp <- if (valid) {
        val expiresAt = Instant.now().plus(timeout)
        val token = sessionTokenGenerator.nextString()
        userSessionTokenStore
          .createToken(authRequest.username, token, expiresAt)
          .map { _ =>
            AuthSuccess(token, timeout)
          }
          .recover {
            case cause: Throwable =>
              log.error(cause, "Unable to create User Session Token")
              AuthFailure
          }
      } else {
        Success(AuthFailure)
      }
    } yield resp

    reply(response)
  }

  private[this] def onLogin(loginRequest: LoginRequest): Unit = {
    reply(userStore.login(loginRequest.username, loginRequest.password).map(LoginResponse))
  }

  private[this] def onValidateSessionToken(validateRequest: ValidateSessionTokenRequest): Unit = {
    val result = for {
      timeout <- configStore.getSessionTimeout()
      username <- userSessionTokenStore.validateUserSessionToken(validateRequest.token, () => Instant.now().plus(timeout))
      authProfile <- getAuthorizationProfile(username)
    } yield ValidateSessionTokenResponse(authProfile)
    reply(result)
  }

  private[this] def onValidateBearerToken(validateRequest: ValidateUserBearerTokenRequest): Unit = {
    val result = for {
      username <- userStore.validateBearerToken(validateRequest.bearerToken)
      authProfile <- getAuthorizationProfile(username)
    } yield ValidateUserBearerTokenResponse(authProfile)

    reply(result)
  }

  private[this] def onValidateApiKey(validateRequest: ValidateUserApiKeyRequest): Unit = {
    reply(for {
      username <- userApiKeyStore.validateUserApiKey(validateRequest.apiKey)
      _ <- userApiKeyStore.setLastUsedForKey(validateRequest.apiKey, Instant.now())
      authProfile <- getAuthorizationProfile(username)
    } yield ValidateUserApiKeyResponse(authProfile))
  }

  private[this] def onGetSessionTokenExpiration(tokenExpirationRequest: GetSessionTokenExpirationRequest): Unit = {
    val result = userSessionTokenStore.expirationCheck(tokenExpirationRequest.token).map(_.map {
      case (username, expiration) =>
        val now = Instant.now()
        SessionTokenExpiration(username, Duration.between(now, expiration))
    }).map(GetSessionTokenExpirationResponse)
    reply(result)
  }

  private[this] def onInvalidateToken(invalidateTokenRequest: InvalidateTokenRequest): Unit = {
    reply(userSessionTokenStore.removeToken(invalidateTokenRequest.token))
  }

  private[this] def getAuthorizationProfile(username: Option[String]): Try[Option[AuthorizationProfileData]] = {
    username match {
      case Some(u) =>
        val userRoles = roleStore.getAllRolesForUser(u)
        val profile = userRoles.map(r => Some(AuthorizationProfileData(u, r)))
        profile
      case None =>
        Success(None)
    }
  }
}

object AuthenticationActor {
  val RelativePath = "AuthActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new AuthenticationActor(dbProvider))

  case class AuthRequest(username: String, password: String) extends CborSerializable

  sealed trait AuthResponse extends CborSerializable

  case class AuthSuccess(token: String, expiration: Duration) extends AuthResponse

  case object AuthFailure extends AuthResponse

  case class LoginRequest(username: String, password: String) extends CborSerializable

  case class LoginResponse(bearerToken: Option[String]) extends CborSerializable

  case class ValidateSessionTokenRequest(token: String) extends CborSerializable

  case class ValidateSessionTokenResponse(profile: Option[AuthorizationProfileData]) extends CborSerializable

  case class ValidateUserBearerTokenRequest(bearerToken: String) extends CborSerializable

  case class ValidateUserBearerTokenResponse(profile: Option[AuthorizationProfileData]) extends CborSerializable

  case class ValidateUserApiKeyRequest(apiKey: String) extends CborSerializable

  case class ValidateUserApiKeyResponse(profile: Option[AuthorizationProfileData]) extends CborSerializable

  case class GetSessionTokenExpirationRequest(token: String) extends CborSerializable

  case class GetSessionTokenExpirationResponse(expiration: Option[SessionTokenExpiration]) extends CborSerializable

  case class InvalidateTokenRequest(token: String) extends CborSerializable

  case class SessionTokenExpiration(username: String, expiration: Duration)

}
