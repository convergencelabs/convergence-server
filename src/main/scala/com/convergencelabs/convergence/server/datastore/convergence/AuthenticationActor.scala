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
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.security.AuthorizationProfileData
import com.convergencelabs.convergence.server.util.RandomStringGenerator
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}


private class AuthenticationActor private[datastore](private[this] val context: ActorContext[AuthenticationActor.Message],
                                             private[this] val dbProvider: DatabaseProvider)
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
            AuthSuccess(token, timeout)
          }
      } else {
        Success(AuthFailure())
      }
    } yield resp) match {
      case Success(resp) =>
        replyTo ! resp
      case Failure(cause) =>
        error("Unable to authenticate user", cause)
        replyTo ! AuthFailure()
    }
  }

  private[this] def onLogin(msg: LoginRequest): Unit = {
    val LoginRequest(username, password, replyTo) = msg
    userStore.login(username, password) match {
      case Success(token) =>
        replyTo ! LoginSuccess(token)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onValidateSessionToken(msg: ValidateSessionTokenRequest): Unit = {
    val ValidateSessionTokenRequest(token, replyTo) = msg
    (for {
      timeout <- configStore.getSessionTimeout()
      username <- userSessionTokenStore.validateUserSessionToken(token, () => Instant.now().plus(timeout))
      authProfile <- getAuthorizationProfile(username)
    } yield authProfile) match {
      case Success(authProfile) =>
        replyTo ! ValidationSuccess(authProfile)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onValidateBearerToken(msg: ValidateUserBearerTokenRequest): Unit = {
    val ValidateUserBearerTokenRequest(bearerToken, replyTo) = msg
    (for {
      username <- userStore.validateBearerToken(bearerToken)
      authProfile <- getAuthorizationProfile(username)
    } yield authProfile) match {
      case Success(authProfile) =>
        replyTo ! ValidationSuccess(authProfile)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onValidateApiKey(msg: ValidateUserApiKeyRequest): Unit = {
    val ValidateUserApiKeyRequest(apiKey, replyTo) = msg
    (for {
      username <- userApiKeyStore.validateUserApiKey(apiKey)
      _ <- userApiKeyStore.setLastUsedForKey(apiKey, Instant.now())
      authProfile <- getAuthorizationProfile(username)
    } yield authProfile) match {
      case Success(authProfile) =>
        replyTo ! ValidationSuccess(authProfile)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetSessionTokenExpiration(msg: GetSessionTokenExpirationRequest): Unit = {
    val GetSessionTokenExpirationRequest(token, replyTo) = msg
    userSessionTokenStore.expirationCheck(token).map(_.map {
      case (username, expiration) =>
        val now = Instant.now()
        SessionTokenExpiration(username, Duration.between(now, expiration))
    }) match {
      case Success(expiration) =>
        replyTo ! GetSessionTokenExpirationSuccess(expiration)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onInvalidateToken(msg: InvalidateSessionTokenRequest): Unit = {
    val InvalidateSessionTokenRequest(token, replyTo) = msg
    userSessionTokenStore.removeToken(token) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
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
  val Key: ServiceKey[Message] = ServiceKey[Message]("AuthenticationActor")

  def apply(dbProvider: DatabaseProvider): Behavior[Message] = Behaviors.setup { context =>
    new AuthenticationActor(context, dbProvider)
  }

  sealed trait Message extends CborSerializable

  //
  // Auth
  //
  case class AuthRequest(username: String, password: String, replyTo: ActorRef[AuthResponse]) extends Message

  sealed trait AuthResponse extends CborSerializable

  case class AuthSuccess(token: String, expiration: Duration) extends AuthResponse

  case class AuthFailure() extends AuthResponse


  //
  // Login
  //
  case class LoginRequest(username: String, password: String, replyTo: ActorRef[LoginResponse]) extends Message

  sealed trait LoginResponse extends CborSerializable

  case class LoginSuccess(bearerToken: Option[String]) extends LoginResponse

  //
  // ValidateSessionToken
  //
  case class ValidateSessionTokenRequest(token: String, replyTo: ActorRef[ValidateResponse]) extends Message

  sealed trait ValidateResponse extends CborSerializable

  case class ValidationSuccess(profile: Option[AuthorizationProfileData]) extends ValidateResponse

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

  sealed trait GetSessionTokenExpirationResponse extends CborSerializable

  case class GetSessionTokenExpirationSuccess(expiration: Option[SessionTokenExpiration]) extends GetSessionTokenExpirationResponse

  //
  // InvalidateTokenRequest
  //
  case class InvalidateSessionTokenRequest(token: String, replyTo: ActorRef[InvalidateSessionTokenResponse]) extends Message

  sealed trait InvalidateSessionTokenResponse extends CborSerializable



  case class RequestFailure(cause: Throwable) extends CborSerializable
    with LoginResponse
    with ValidateResponse
    with GetSessionTokenExpirationResponse
    with InvalidateSessionTokenResponse



  case class RequestSuccess() extends CborSerializable
    with InvalidateSessionTokenResponse

  //
  // Data Structures
  //
  case class SessionTokenExpiration(username: String, expiration: Duration)

}
