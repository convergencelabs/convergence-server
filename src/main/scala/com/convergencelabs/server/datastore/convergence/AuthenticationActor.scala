/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence

import java.time.{Duration, Instant}

import akka.actor.{ActorLogging, Props}
import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.security.AuthorizationProfile
import com.convergencelabs.server.util.RandomStringGenerator

import scala.util.{Success, Try}

object AuthenticationActor {
  val RelativePath = "AuthActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new AuthenticationActor(dbProvider))

  case class AuthRequest(username: String, password: String)

  sealed trait AuthResponse
  case class AuthSuccess(token: String, expiration: Duration) extends AuthResponse
  case object AuthFailure extends AuthResponse

  case class LoginRequest(username: String, password: String)

  case class ValidateSessionTokenRequest(token: String)
  case class ValidateUserBearerTokenRequest(bearerToken: String)
  case class ValidateUserApiKeyRequest(apiKey: String)

  case class GetSessionTokenExpirationRequest(token: String)
  case class SessionTokenExpiration(username: String, expiration: Duration)

  case class InvalidateTokenRequest(token: String)
}

class AuthenticationActor private[datastore] (private[this] val dbProvider: DatabaseProvider)
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
      login(loginRequest)
    case validateRequest: ValidateSessionTokenRequest =>
      validateSessionToken(validateRequest)
    case validateUserBearerTokenRequest: ValidateUserBearerTokenRequest =>
      validateBearerToken(validateUserBearerTokenRequest)
    case validateUserApiKey: ValidateUserApiKeyRequest =>
      validateApiKey(validateUserApiKey)
    case tokenExpirationRequest: GetSessionTokenExpirationRequest =>
      retrieveSessionTokenExpiration(tokenExpirationRequest)
    case invalidateTokenRequest: InvalidateTokenRequest =>
      invalidateToken(invalidateTokenRequest)
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

  private[this] def login(loginRequest: LoginRequest): Unit = {
    reply(userStore.login(loginRequest.username, loginRequest.password))
  }

  private[this] def validateSessionToken(validateRequest: ValidateSessionTokenRequest): Unit = {
    reply(for {
      timeout <- configStore.getSessionTimeout()
      username <- userSessionTokenStore.validateUserSessionToken(validateRequest.token, () => Instant.now().plus(timeout))
      authProfile <- getAuthorizationProfile(username)
    } yield authProfile)
  }

  private[this] def validateBearerToken(validateRequest: ValidateUserBearerTokenRequest): Unit = {
    reply(for {
      username <- userStore.validateBearerToken(validateRequest.bearerToken)
      authProfile <- getAuthorizationProfile(username)
    } yield authProfile)
  }

  private[this] def validateApiKey(validateRequest: ValidateUserApiKeyRequest): Unit = {
    reply(for {
      username <- userApiKeyStore.validateUserApiKey(validateRequest.apiKey)
      _ <- userApiKeyStore.setLastUsedForKey(validateRequest.apiKey, Instant.now())
      authProfile <- getAuthorizationProfile(username)
    } yield authProfile)
  }

  private[this] def retrieveSessionTokenExpiration(tokenExpirationRequest: GetSessionTokenExpirationRequest): Unit = {
    val result = userSessionTokenStore.expirationCheck(tokenExpirationRequest.token).map(_.map {
      case (username, expiration) =>
        val now = Instant.now()
        SessionTokenExpiration(username, Duration.between(now, expiration))
    })
    reply(result)
  }

  private[this] def invalidateToken(invalidateTokenRequest: InvalidateTokenRequest): Unit = {
    reply(userSessionTokenStore.removeToken(invalidateTokenRequest.token))
  }

  private[this] def getAuthorizationProfile(username: Option[String]): Try[Option[AuthorizationProfile]] = {
    username match {
      case Some(u) =>
        val userRoles = roleStore.getAllRolesForUser(u)
        val profile = userRoles.map(r => Some(new AuthorizationProfile(u, r)))
        profile
      case None =>
        Success(None)
    }
  }
}
