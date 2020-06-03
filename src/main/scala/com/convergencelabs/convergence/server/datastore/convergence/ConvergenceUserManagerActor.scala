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

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.convergence.UserStore.User
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.util.concurrent.FutureUtils
import grizzled.slf4j.Logging

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

class ConvergenceUserManagerActor private[datastore](private[this] val context: ActorContext[ConvergenceUserManagerActor.Message],
                                                     private[this] val dbProvider: DatabaseProvider,
                                                     private[this] val domainStoreActor: ActorRef[DomainStoreActor.Message])
  extends AbstractBehavior[ConvergenceUserManagerActor.Message](context) with Logging {

  import ConvergenceUserManagerActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

  // FIXME: Read this from configuration
  private[this] implicit val requestTimeout: Timeout = Timeout(5 seconds)
  private[this] implicit val executionContext: ExecutionContextExecutor = context.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system

  private[this] val userStore: UserStore = new UserStore(dbProvider)
  private[this] val roleStore: RoleStore = new RoleStore(dbProvider)

  private[this] val userCreator = new UserCreator(dbProvider)

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case message: CreateConvergenceUserRequest =>
        onCreateConvergenceUser(message)
      case message: DeleteConvergenceUserRequest =>
        onDeleteConvergenceUser(message)
      case message: GetConvergenceUserRequest =>
        onGetConvergenceUser(message)
      case message: GetConvergenceUsersRequest =>
        onGetConvergenceUsers(message)
      case message: UpdateConvergenceUserProfileRequest =>
        onUpdateConvergenceUserProfile(message)
      case message: UpdateConvergenceUserRequest =>
        onUpdateConvergenceUser(message)
      case message: SetPasswordRequest =>
        onSetUserPassword(message)
      case message: GetUserBearerTokenRequest =>
        onGetUserBearerToken(message)
      case message: RegenerateUserBearerTokenRequest =>
        onRegenerateUserBearerToken(message)
    }

    Behaviors.same
  }


  private[this] def onCreateConvergenceUser(message: CreateConvergenceUserRequest): Unit = {
    val CreateConvergenceUserRequest(username, email, firstName, lastName, displayName, password, serverRole, replyTo) = message
    val user = User(username, email, firstName, lastName, displayName, None)
    userCreator.createUser(user, password, serverRole) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetConvergenceUser(message: GetConvergenceUserRequest): Unit = {
    val GetConvergenceUserRequest(username, replyTo) = message
    (for {
      user <- userStore.getUserByUsername(username)
      roles <- roleStore.getRolesForUsersAndTarget(Set(username), ServerRoleTarget())
    } yield {
      user.map { u =>
        val globalRole = roles.get(u.username).flatMap(_.headOption).getOrElse("")
        ConvergenceUserInfo(u, globalRole)
      }
    }) match {
      case Success(user) =>
        replyTo ! GetConvergenceUserSuccess(user)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetConvergenceUsers(message: GetConvergenceUsersRequest): Unit = {
    val GetConvergenceUsersRequest(filter, limit, offset, replyTo) = message
    (for {
      users <- userStore.getUsers(filter, limit, offset)
      roles <- roleStore.getRolesForUsersAndTarget(users.map(_.username).toSet, ServerRoleTarget())
    } yield {
      users.map { user =>
        val globalRole = roles.get(user.username).flatMap(_.headOption).getOrElse("")
        ConvergenceUserInfo(user, globalRole)
      }.toSet
    }) match {
      case Success(users) =>
        replyTo ! GetConvergenceUsersSuccess(users)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onDeleteConvergenceUser(message: DeleteConvergenceUserRequest): Unit = {
    val DeleteConvergenceUserRequest(username, replyTo) = message
    domainStoreActor.ask[DomainStoreActor.DeleteDomainsForUserResponse](ref => DomainStoreActor.DeleteDomainsForUserRequest(username, ref))
      .flatMap(_ => FutureUtils.tryToFuture(userStore.deleteUser(username))) onComplete {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onUpdateConvergenceUserProfile(message: UpdateConvergenceUserProfileRequest): Unit = {
    val UpdateConvergenceUserProfileRequest(username, email, firstName, lastName, displayName, replyTo) = message
    val update = User(username, email, firstName, lastName, displayName, None)
    userStore.updateUser(update) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onUpdateConvergenceUser(message: UpdateConvergenceUserRequest): Unit = {
    val UpdateConvergenceUserRequest(username, email, firstName, lastName, displayName, globalRole, replyTo) = message
    val update = User(username, email, firstName, lastName, displayName, None)
    (for {
      - <- userStore.updateUser(update)
      _ <- roleStore.setUserRolesForTarget(username, ServerRoleTarget(), Set(globalRole))
    } yield ()) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onSetUserPassword(message: SetPasswordRequest): Unit = {
    val SetPasswordRequest(username, password, replyTo) = message
    userStore.setUserPassword(username, password) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetUserBearerToken(message: GetUserBearerTokenRequest): Unit = {
    val GetUserBearerTokenRequest(username, replyTo) = message
    userStore.getBearerToken(username) match {
      case Success(token) =>
        replyTo ! GetUserBearerTokenSuccess(token)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onRegenerateUserBearerToken(message: RegenerateUserBearerTokenRequest): Unit = {
    val RegenerateUserBearerTokenRequest(username, replyTo) = message
    val bearerToken = userCreator.bearerTokenGen.nextString()
    userStore.setBearerToken(username, bearerToken) match {
      case Success(_) =>
        replyTo ! RegenerateUserBearerTokenSuccess(bearerToken)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }
}


object ConvergenceUserManagerActor {
  val Key: ServiceKey[Message] = ServiceKey[Message]("ConvergenceUserManagerActor")

  def apply(dbProvider: DatabaseProvider, domainStoreActor: ActorRef[DomainStoreActor.Message]): Behavior[Message] =
    Behaviors.setup(context => new ConvergenceUserManagerActor(context, dbProvider, domainStoreActor))

  case class ConvergenceUserInfo(user: User, globalRole: String)

  sealed trait Message extends CborSerializable


  //
  // CreateConvergenceUser
  //
  case class CreateConvergenceUserRequest(username: String,
                                          email: String,
                                          firstName: String,
                                          lastName: String,
                                          displayName: String,
                                          password: String,
                                          globalRole: String,
                                          replyTo: ActorRef[CreateConvergenceUserResponse]) extends Message

  sealed trait CreateConvergenceUserResponse extends CborSerializable

  //
  //UpdateConvergenceUser
  //
  case class UpdateConvergenceUserRequest(username: String,
                                          email: String,
                                          firstName: String,
                                          lastName: String,
                                          displayName: String,
                                          globalRole: String,
                                          replyTo: ActorRef[UpdateConvergenceUserResponse]) extends Message

  sealed trait UpdateConvergenceUserResponse extends CborSerializable

  //
  // UpdateConvergenceUserProfile
  //
  case class UpdateConvergenceUserProfileRequest(username: String,
                                                 email: String,
                                                 firstName: String,
                                                 lastName: String,
                                                 displayName: String,
                                                 replyTo: ActorRef[UpdateConvergenceUserProfileResponse]) extends Message

  sealed trait UpdateConvergenceUserProfileResponse extends CborSerializable

  //
  // SetPassword
  //
  case class SetPasswordRequest(username: String, password: String, replyTo: ActorRef[SetPasswordResponse]) extends Message

  sealed trait SetPasswordResponse extends CborSerializable

  //
  // DeleteConvergenceUser
  //
  case class DeleteConvergenceUserRequest(username: String, replyTo: ActorRef[DeleteConvergenceUserResponse]) extends Message

  sealed trait DeleteConvergenceUserResponse extends CborSerializable

  //
  // GetConvergenceUsers
  //
  case class GetConvergenceUsersRequest(filter: Option[String], limit: Option[Int], offset: Option[Int], replyTo: ActorRef[GetConvergenceUsersResponse]) extends Message

  sealed trait GetConvergenceUsersResponse extends CborSerializable

  case class GetConvergenceUsersSuccess(users: Set[ConvergenceUserInfo]) extends GetConvergenceUsersResponse

  //
  // GetConvergenceUser
  //
  case class GetConvergenceUserRequest(username: String, replyTo: ActorRef[GetConvergenceUserResponse]) extends Message

  sealed trait GetConvergenceUserResponse extends CborSerializable

  case class GetConvergenceUserSuccess(user: Option[ConvergenceUserInfo]) extends GetConvergenceUserResponse

  //
  // GetUserBearerToken
  //
  case class GetUserBearerTokenRequest(username: String, replyTo: ActorRef[GetUserBearerTokenResponse]) extends Message

  sealed trait GetUserBearerTokenResponse extends CborSerializable

  case class GetUserBearerTokenSuccess(token: Option[String]) extends GetUserBearerTokenResponse

  //
  // RegenerateUserBearerToken
  //
  case class RegenerateUserBearerTokenRequest(username: String, replyTo: ActorRef[RegenerateUserBearerTokenResponse]) extends Message

  sealed trait RegenerateUserBearerTokenResponse extends CborSerializable

  case class RegenerateUserBearerTokenSuccess(username: String) extends RegenerateUserBearerTokenResponse

  //
  // Generic Responses
  //
  case class RequestFailure(cause: Throwable) extends CborSerializable
    with SetPasswordResponse
    with DeleteConvergenceUserResponse
    with UpdateConvergenceUserProfileResponse
    with UpdateConvergenceUserResponse
    with CreateConvergenceUserResponse
    with GetConvergenceUsersResponse
    with GetConvergenceUserResponse
    with GetUserBearerTokenResponse
    with RegenerateUserBearerTokenResponse


  case class RequestSuccess() extends CborSerializable
    with SetPasswordResponse
    with DeleteConvergenceUserResponse
    with UpdateConvergenceUserProfileResponse
    with UpdateConvergenceUserResponse
    with CreateConvergenceUserResponse

}
