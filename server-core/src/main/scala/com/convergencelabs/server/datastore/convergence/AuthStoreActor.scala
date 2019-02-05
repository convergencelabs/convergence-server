package com.convergencelabs.server.datastore.convergence

import java.time.Duration
import java.time.Instant

import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.StoreActor

import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.util.RandomStringGenerator
import scala.util.Success

object AuthStoreActor {
  val RelativePath = "AuthStoreActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new AuthStoreActor(dbProvider))

  case class AuthRequest(username: String, password: String)

  sealed trait AuthResponse
  case class AuthSuccess(token: String, expiration: Duration) extends AuthResponse
  case object AuthFailure extends AuthResponse

  case class LoginRequest(username: String, password: String)

  case class ValidateSessionTokenRequest(token: String)
  case class ValidateUserBearerTokenRequest(bearerToken: String)

  case class GetSessionTokenExpirationRequest(token: String)
  case class SessionTokenExpiration(username: String, expiration: Duration)

  case class InvalidateTokenRequest(token: String)
}

class AuthStoreActor private[datastore] (private[this] val dbProvider: DatabaseProvider)
  extends StoreActor with ActorLogging {

  import AuthStoreActor._

  val tokenDuration = context.system.settings.config.getDuration("convergence.rest.session-token-expiration")

  private[this] val userStore = new UserStore(dbProvider)
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
    case tokenExpirationRequest: GetSessionTokenExpirationRequest =>
      getSessionTokenExpiration(tokenExpirationRequest)
    case invalidateTokenRequest: InvalidateTokenRequest =>
      invalidateToken(invalidateTokenRequest)
    case message: Any =>
      unhandled(message)
  }

  private[this] def authenticateUser(authRequest: AuthRequest): Unit = {
    reply(userStore.validateCredentials(authRequest.username, authRequest.password).flatMap(_ match {
      case true =>
        val expiresAt = Instant.now().plus(tokenDuration)
        val token = sessionTokenGenerator.nextString()
        userSessionTokenStore.createToken(authRequest.username, token, expiresAt).map(_ => AuthSuccess(token, tokenDuration))
      case false =>
        Success(AuthFailure)
    }))
  }

  private[this] def login(loginRequest: LoginRequest): Unit = {
    reply(userStore.login(loginRequest.username, loginRequest.password))
  }

  private[this] def validateSessionToken(validateRequest: ValidateSessionTokenRequest): Unit = {
    reply(userSessionTokenStore.validateUserSessionToken(validateRequest.token, () => Instant.now().plus(tokenDuration)))
  }

  private[this] def validateBearerToken(validateRequest: ValidateUserBearerTokenRequest): Unit = {
    reply(userStore.validateBearerToken(validateRequest.bearerToken))
  }

  private[this] def getSessionTokenExpiration(tokenExpirationRequest: GetSessionTokenExpirationRequest): Unit = {
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
}
