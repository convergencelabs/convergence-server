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

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.security.AuthorizationProfileData
import com.convergencelabs.convergence.server.util.RandomStringGenerator
import grizzled.slf4j.Logging

import scala.util.{Success, Try}

class AuthenticationActor private[datastore](context: ActorContext[AuthenticationActor.Message],
                                                     dbProvider: DatabaseProvider)
  extends AbstractBehavior[AuthenticationActor.Message](context) with Logging {

  import AuthenticationActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

  private[this] val userStore = new UserStore(dbProvider)
  private[this] val userApiKeyStore = new UserApiKeyStore(dbProvider)
  private[this] val roleStore = new RoleStore(dbProvider)
  private[this] val configStore = new ConfigStore(dbProvider)
  private[this] val userSessionTokenStore = new UserSessionTokenStore(dbProvider)
  private[this] val sessionTokenGenerator = new RandomStringGenerator(length = 32)

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case authRequest: AuthRequest =>
        onAuthRequest(authRequest)
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
      case invalidateTokenRequest: InvalidateSessionTokenRequest =>
        onInvalidateToken(invalidateTokenRequest)
    }
    Behaviors.same
  }

  private[this] def onAuthRequest(msg: AuthRequest): Unit = {
    val AuthRequest(username, password, replyTo) = msg
    (for {
      timeout <- configStore.getSessionTimeout()
      valid <- userStore.validateCredentials(username, password)
      resp <- if (valid) {
        val expiresAt = Instant.now().plus(timeout)
        val token = sessionTokenGenerator.nextString()
        userSessionTokenStore
          .createToken(username, token, expiresAt)
          .map { _ =>
            AuthResponse(Right(AuthData(token, timeout)))
          }
      } else {
        Success(AuthResponse(Left(AuthenticationFailed())))
      }
    } yield resp)
      .recover { cause =>
        error("Unable to authenticate user", cause)
        AuthResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onLogin(msg: LoginRequest): Unit = {
    val LoginRequest(username, password, replyTo) = msg
    userStore.login(username, password)
      .map(_.map(t => LoginResponse(Right(t))).getOrElse(LoginResponse(Left(LoginFailed()))))
      .recover {
        cause =>
          error("Unexpected error handling login request", cause)
          LoginResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onValidateSessionToken(msg: ValidateSessionTokenRequest): Unit = {
    val ValidateSessionTokenRequest(token, replyTo) = msg
    val username = () => configStore.getSessionTimeout().flatMap { timeout =>
      userSessionTokenStore.validateUserSessionToken(token, () => Instant.now().plus(timeout))
    }
    this.validate(username, replyTo)
  }

  private[this] def onValidateBearerToken(msg: ValidateUserBearerTokenRequest): Unit = {
    val ValidateUserBearerTokenRequest(bearerToken, replyTo) = msg
    val username = () => userStore.validateBearerToken(bearerToken)
    this.validate(username, replyTo)
  }

  private[this] def onValidateApiKey(msg: ValidateUserApiKeyRequest): Unit = {
    val ValidateUserApiKeyRequest(apiKey, replyTo) = msg
    val username = () => userApiKeyStore.validateUserApiKey(apiKey).flatMap { username =>
      userApiKeyStore.setLastUsedForKey(apiKey, Instant.now()).map(_ => username)
    }

    this.validate(username, replyTo)
  }

  private[this] def validate(username: () => Try[Option[String]], replyTo: ActorRef[ValidateResponse]): Unit = {
    (for {
      username <- username()
      authProfile <- getAuthorizationProfile(username)
        .map(p =>
          p.map(profile => ValidateResponse(Right(profile)))
            .getOrElse(ValidateResponse(Left(ValidationFailed())))
        )
    } yield authProfile)
      .recover { cause =>
        error("Unexpected error validating user", cause)
        ValidateResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetSessionTokenExpiration(msg: GetSessionTokenExpirationRequest): Unit = {
    val GetSessionTokenExpirationRequest(token, replyTo) = msg
    userSessionTokenStore.expirationCheck(token)
      .map(_.map {
        case (username, expiration) =>
          val now = Instant.now()
          val data = SessionTokenExpiration(username, Duration.between(now, expiration))
          GetSessionTokenExpirationResponse(Right(data))
      }.getOrElse(GetSessionTokenExpirationResponse(Left(TokenNotFoundError())))
      )
      .recover { cause =>
        error("Unexpected error getting session token expiration", cause)
        GetSessionTokenExpirationResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onInvalidateToken(msg: InvalidateSessionTokenRequest): Unit = {
    val InvalidateSessionTokenRequest(token, replyTo) = msg
    userSessionTokenStore.removeToken(token)
      .map(_ => InvalidateSessionTokenResponse(Right(())))
      .recover { cause =>
        error("Unexpected error invalidating session token", cause)
        InvalidateSessionTokenResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
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

object AuthenticationActor extends AuthenticationActorProtocol {
  val Key: ServiceKey[Message] = ServiceKey[Message]("AuthenticationActor")

  def apply(dbProvider: DatabaseProvider): Behavior[Message] = Behaviors.setup { context =>
    new AuthenticationActor(context, dbProvider)
  }
}
