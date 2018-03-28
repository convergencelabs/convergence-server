package com.convergencelabs.server.datastore

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.ActorLogging
import akka.actor.Props
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.time.Duration
import com.convergencelabs.server.datastore.AuthStoreActor.InvalidateTokenRequest

class AuthStoreActor private[datastore] (
  private[this] val dbProvider: DatabaseProvider)
    extends StoreActor with ActorLogging {

  import AuthStoreActor._

  val tokenDuration = context.system.settings.config.getDuration("convergence.rest.auth-token-expiration")

  private[this] val userStore: UserStore = new UserStore(dbProvider, tokenDuration)

  def receive: Receive = {
    case authRequest: AuthRequest =>
      authenticateUser(authRequest)
    case loginRequest: LoginRequest =>
      login(loginRequest)
    case validateRequest: ValidateSessionTokenRequest =>
      validateSessionToken(validateRequest)
    case validateApiKeyRequest: ValidateUserApiKeyRequest =>
      validateUserApiKey(validateApiKeyRequest)
    case tokenExpirationRequest: GetSessionTokenExpirationRequest =>
      getSessionTokenExpiration(tokenExpirationRequest)
    case invalidateTokenRequest: InvalidateTokenRequest =>
      invalidateToken(invalidateTokenRequest)
    case message: Any =>
      unhandled(message)
  }

  private[this] def authenticateUser(authRequest: AuthRequest): Unit = {
    mapAndReply(userStore.validateCredentials(authRequest.username, authRequest.password)) {
      case Some((token, expiration)) =>
        val delta = Duration.between(Instant.now(), expiration)
        AuthSuccess(token, delta)
      case None =>
        AuthFailure
    }
  }

  private[this] def login(loginRequest: LoginRequest): Unit = {
    reply(userStore.login(loginRequest.username, loginRequest.password))
  }

  private[this] def validateSessionToken(validateRequest: ValidateSessionTokenRequest): Unit = {
    reply(userStore.validateUserSessionToken(validateRequest.token))
  }
  
  private[this] def validateUserApiKey(validateRequest: ValidateUserApiKeyRequest): Unit = {
    reply(userStore.validateUserApiKey(validateRequest.apiKey))
  }

  private[this] def getSessionTokenExpiration(tokenExpirationRequest: GetSessionTokenExpirationRequest): Unit = {
    val result = userStore.expirationCheck(tokenExpirationRequest.token).map(_.map {
      case (username, expiration) =>
        val now = Instant.now()
        SessionTokenExpiration(username, Duration.between(now, expiration))
    })
    reply(result)
  }

  private[this] def invalidateToken(invalidateTokenRequest: InvalidateTokenRequest): Unit = {
    reply(userStore.removeToken(invalidateTokenRequest.token))
  }
}

object AuthStoreActor {
  val RelativePath = "AuthStoreActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new AuthStoreActor(dbProvider))

  case class AuthRequest(username: String, password: String)

  sealed trait AuthResponse
  case class AuthSuccess(token: String, expiration: Duration) extends AuthResponse
  case object AuthFailure extends AuthResponse

  case class LoginRequest(username: String, password: String)

  case class ValidateSessionTokenRequest(token: String)
  case class ValidateUserApiKeyRequest(apiKey: String)

  case class GetSessionTokenExpirationRequest(token: String)
  case class SessionTokenExpiration(username: String, expiration: Duration)

  case class InvalidateTokenRequest(token: String)
}
