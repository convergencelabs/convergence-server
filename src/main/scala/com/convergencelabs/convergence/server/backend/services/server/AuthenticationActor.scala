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

package com.convergencelabs.convergence.server.backend.services.server

import java.time.{Duration, Instant}

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.backend.datastore.convergence._
import com.convergencelabs.convergence.server.security.AuthorizationProfileData
import com.convergencelabs.convergence.server.util.RandomStringGenerator
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

import scala.util.{Success, Try}

/**
 * The [[AuthenticationActor]] actor is responsible for handling authentication.
 * of Convergence users from the HTTP API.
 *
 * @param context               The actor context for this actor.
 * @param userStore             The user store used for validating users.
 * @param userApiKeyStore       The api key store used for validating user API Keys.
 * @param roleStore             The role store used for constructing the authentication profile
 * @param configStore           The config store that provide authentication configuration.
 * @param userSessionTokenStore The store to create and update HTTP API Sessions
 */
class AuthenticationActor private(context: ActorContext[AuthenticationActor.Message],
                                  userStore: UserStore,
                                  userApiKeyStore: UserApiKeyStore,
                                  roleStore: RoleStore,
                                  configStore: ConfigStore,
                                  userSessionTokenStore: UserSessionTokenStore)
  extends AbstractBehavior[AuthenticationActor.Message](context) {

  import AuthenticationActor._

  private[this] val sessionTokenGenerator = new RandomStringGenerator(length = 32)

  context.system.receptionist ! Receptionist.Register(Key, context.self)

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
        context.log.error("Unable to authenticate user", cause)
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
          context.log.error("Unexpected error handling login request", cause)
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
        context.log.error("Unexpected error validating user", cause)
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
        context.log.error("Unexpected error getting session token expiration", cause)
        GetSessionTokenExpirationResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onInvalidateToken(msg: InvalidateSessionTokenRequest): Unit = {
    val InvalidateSessionTokenRequest(token, replyTo) = msg
    userSessionTokenStore.removeToken(token)
      .map(_ => InvalidateSessionTokenResponse(Right(Ok())))
      .recover { cause =>
        context.log.error("Unexpected error invalidating session token", cause)
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

object AuthenticationActor {
  val Key: ServiceKey[Message] = ServiceKey[Message]("AuthenticationActor")

  def apply(userStore: UserStore,
            userApiKeyStore: UserApiKeyStore,
            roleStore: RoleStore,
            configStore: ConfigStore,
            userSessionTokenStore: UserSessionTokenStore): Behavior[Message] = Behaviors.setup(context =>
    new AuthenticationActor(context, userStore, userApiKeyStore, roleStore, configStore, userSessionTokenStore)
  )

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  //
  // Auth
  //
  final case class AuthRequest(username: String, password: String, replyTo: ActorRef[AuthResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[AuthenticationFailed], name = "auth_failure"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait AuthError

  final case class AuthenticationFailed() extends AuthError

  final case class AuthData(token: String, expiration: Duration)

  final case class AuthResponse(data: Either[AuthError, AuthData]) extends CborSerializable


  //
  // Login
  //
  final case class LoginRequest(username: String, password: String, replyTo: ActorRef[LoginResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[LoginFailed], name = "login_failure"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait LoginError extends CborSerializable

  final case class LoginFailed() extends LoginError

  final case class LoginResponse(bearerToken: Either[LoginError, String]) extends CborSerializable

  //
  // ValidateResponse
  //

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ValidationFailed], name = "validation_failure"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait ValidateError

  final case class ValidationFailed() extends ValidateError

  final case class ValidateResponse(profile: Either[ValidateError, AuthorizationProfileData]) extends CborSerializable


  //
  // ValidateSessionToken
  //
  final case class ValidateSessionTokenRequest(token: String, replyTo: ActorRef[ValidateResponse]) extends Message


  //
  // ValidateUserBearerToken
  //
  final case class ValidateUserBearerTokenRequest(bearerToken: String, replyTo: ActorRef[ValidateResponse]) extends Message

  //
  // ValidateUserApiKey
  //
  final case class ValidateUserApiKeyRequest(apiKey: String, replyTo: ActorRef[ValidateResponse]) extends Message

  //
  // GetSessionTokenExpiration
  //
  final case class GetSessionTokenExpirationRequest(token: String, replyTo: ActorRef[GetSessionTokenExpirationResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[TokenNotFoundError], name = "token_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetSessionTokenExpirationError

  final case class TokenNotFoundError() extends GetSessionTokenExpirationError

  final case class SessionTokenExpiration(username: String, expiration: Duration)

  final case class GetSessionTokenExpirationResponse(expiration: Either[GetSessionTokenExpirationError, SessionTokenExpiration]) extends CborSerializable

  //
  // InvalidateTokenRequest
  //
  final case class InvalidateSessionTokenRequest(token: String, replyTo: ActorRef[InvalidateSessionTokenResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait InvalidateSessionTokenError

  final case class InvalidateSessionTokenResponse(response: Either[InvalidateSessionTokenError, Ok]) extends CborSerializable

  //
  // Common Errors
  //

  final case class UnknownError() extends AuthError
    with LoginError
    with ValidateError
    with GetSessionTokenExpirationError
    with InvalidateSessionTokenError

}
