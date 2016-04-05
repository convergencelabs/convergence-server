package com.convergencelabs.server.datastore

import scala.util.Try
import akka.actor.Actor
import akka.actor.ActorLogging
import com.convergencelabs.server.datastore.AuthStoreActor._
import scala.util.Success
import scala.util.Failure
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

class AuthStoreActor private[datastore] (private[this] val dbPool: OPartitionedDatabasePool)
    extends Actor with ActorLogging {

  private[this] val userStore: UserStore = new UserStore(dbPool)

  def receive: Receive = {
    case authRequest: AuthRequest         => sender ! authenticateUser(authRequest)
    case validateRequest: ValidateRequest => sender ! validateToken(validateRequest)
    case message: Any                     => unhandled(message)
  }

  def authenticateUser(authRequest: AuthRequest): Try[AuthResponse] = {
    userStore.validateCredentials(authRequest.username, authRequest.password) flatMap (v => Try(AuthResponse(v._1, v._2)))
  }

  def validateToken(validateRequest: ValidateRequest): Try[ValidateResponse] = {
    userStore.validateToken(validateRequest.token) flatMap (v => Try(ValidateResponse(v._1, v._2)))
  }
}

object AuthStoreActor {
  case class AuthRequest(username: String, password: String)
  case class AuthResponse(ok: Boolean, token: Option[String])
  case class ValidateRequest(token: String)
  case class ValidateResponse(ok: Boolean, uid: Option[String])
}

