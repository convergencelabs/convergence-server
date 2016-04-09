package com.convergencelabs.server.datastore

import scala.util.Try
import akka.actor.Actor
import akka.actor.ActorLogging
import com.convergencelabs.server.datastore.AuthStoreActor._
import scala.util.Success
import scala.util.Failure
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import akka.actor.Props
import akka.actor.Status


import ReplyUtil.ReplyTry

class AuthStoreActor private[datastore] (private[this] val dbPool: OPartitionedDatabasePool)
    extends Actor with ActorLogging {
  

  
  private[this] val userStore: UserStore = new UserStore(dbPool)

  def receive: Receive = {
    case authRequest: AuthRequest         => authenticateUser(authRequest)
    case validateRequest: ValidateRequest => validateToken(validateRequest)
    case message: Any                     => unhandled(message)
  }
  
  private[this] def authenticateUser(authRequest: AuthRequest): Unit = {
    sender ! (userStore.validateCredentials(authRequest.username, authRequest.password) mapReply {
      case Some((uid, token)) => AuthSuccess(uid, token)
      case None               => AuthFailure
    })
  }

  private[this] def validateToken(validateRequest: ValidateRequest): Unit = {
    sender !(userStore.validateToken(validateRequest.token) mapReply {
      case Some(userId) => ValidateSuccess(userId)
      case None         => ValidateFailure
    })
  }
}

object AuthStoreActor {
  def props(dbPool: OPartitionedDatabasePool): Props = Props(new AuthStoreActor(dbPool))
  
  case class AuthRequest(username: String, password: String)

  sealed trait AuthResponse
  case class AuthSuccess(uid: String, token: String) extends AuthResponse
  case object AuthFailure extends AuthResponse

  case class ValidateRequest(token: String)

  sealed trait ValidateResponse
  case class ValidateSuccess(uid: String) extends ValidateResponse
  case object ValidateFailure extends ValidateResponse
}
