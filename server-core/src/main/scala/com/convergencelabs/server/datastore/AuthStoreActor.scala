package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.AuthStoreActor.AuthFailure
import com.convergencelabs.server.datastore.AuthStoreActor.AuthRequest
import com.convergencelabs.server.datastore.AuthStoreActor.AuthSuccess
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateFailure
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateRequest
import com.convergencelabs.server.datastore.AuthStoreActor.ValidateSuccess
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.ActorLogging
import akka.actor.Props
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import java.time.Instant
import com.convergencelabs.server.datastore.AuthStoreActor.TokenExpirationRequest
import com.convergencelabs.server.datastore.AuthStoreActor.TokenExpirationSuccess
import com.convergencelabs.server.datastore.AuthStoreActor.TokenExpirationFailure

class AuthStoreActor private[datastore] (
  private[this] val dbPool: OPartitionedDatabasePool)
    extends StoreActor with ActorLogging {

  val tokenDuration = context.system.settings.config.getDuration("convergence.rest.auth-token-expiration")

  private[this] val userStore: UserStore = new UserStore(dbPool, tokenDuration)

  def receive: Receive = {
    case authRequest: AuthRequest                       => authenticateUser(authRequest)
    case validateRequest: ValidateRequest               => validateToken(validateRequest)
    case tokenExpirationRequest: TokenExpirationRequest => expirationRequest(tokenExpirationRequest)
    case message: Any                                   => unhandled(message)
  }

  private[this] def authenticateUser(authRequest: AuthRequest): Unit = {
    mapAndReply(userStore.validateCredentials(authRequest.username, authRequest.password)) {
      case Some((token, expiration)) => AuthSuccess(token, expiration)
      case None =>
        AuthFailure
    }
  }

  private[this] def validateToken(validateRequest: ValidateRequest): Unit = {
    mapAndReply(userStore.validateToken(validateRequest.token)) {
      case Some(username) => ValidateSuccess(username)
      case None           => ValidateFailure
    }
  }

  private[this] def expirationRequest(tokenExpirationRequest: TokenExpirationRequest): Unit = {
    mapAndReply(userStore.expirationCheck(tokenExpirationRequest.token)) {
      case Some((username, expiration)) => TokenExpirationSuccess(username, expiration)
      case None                         => TokenExpirationFailure
    }
  }
}

object AuthStoreActor {
  def props(dbPool: OPartitionedDatabasePool): Props = Props(new AuthStoreActor(dbPool))

  case class AuthRequest(username: String, password: String)

  sealed trait AuthResponse
  case class AuthSuccess(token: String, expiration: Instant) extends AuthResponse
  case object AuthFailure extends AuthResponse

  case class ValidateRequest(token: String)

  sealed trait ValidateResponse
  case class ValidateSuccess(username: String) extends ValidateResponse
  case object ValidateFailure extends ValidateResponse

  case class TokenExpirationRequest(token: String)

  sealed trait TokenExpirationResponse
  case class TokenExpirationSuccess(username: String, expiration: Instant) extends TokenExpirationResponse
  case object TokenExpirationFailure extends TokenExpirationResponse
}
