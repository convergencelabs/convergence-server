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

import akka.actor.{ActorLogging, ActorRef, Props, Status, actorRef2Scala}
import akka.util.Timeout
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.datastore.convergence.DomainStoreActor.DeleteDomainsForUserRequest
import com.convergencelabs.convergence.server.datastore.convergence.UserStore.User
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.util.concurrent.FutureUtils

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class ConvergenceUserManagerActor private[datastore](private[this] val dbProvider: DatabaseProvider,
                                                     private[this] val domainStoreActor: ActorRef)
  extends StoreActor
    with ActorLogging {

  import ConvergenceUserManagerActor._
  import akka.pattern.ask

  // FIXME: Read this from configuration
  private[this] implicit val requestTimeout: Timeout = Timeout(2 seconds)
  private[this] implicit val executionContext: ExecutionContextExecutor = context.dispatcher

  private[this] val userStore: UserStore = new UserStore(dbProvider)
  private[this] val roleStore: RoleStore = new RoleStore(dbProvider)

  private[this] val userCreator = new UserCreator(dbProvider)

  def receive: Receive = {
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
    case message: Any =>
      unhandled(message)
  }

  private[this] def onCreateConvergenceUser(message: CreateConvergenceUserRequest): Unit = {
    val CreateConvergenceUserRequest(username, email, firstName, lastName, displayName, password, serverRole) = message
    val origSender = sender
    val user = User(username, email, firstName, lastName, displayName, None)
    userCreator.createUser(user, password, serverRole)
      .map(_ => origSender ! (()))
      .recover {
        case e: Throwable =>
          origSender ! Status.Failure(e)
      }
  }

  private[this] def onGetConvergenceUser(message: GetConvergenceUserRequest): Unit = {
    val GetConvergenceUserRequest(username) = message
    val convergenceUser = (for {
      user <- userStore.getUserByUsername(username)
      roles <- roleStore.getRolesForUsersAndTarget(Set(username), ServerRoleTarget())
    } yield {
      user.map { u =>
        val globalRole = roles.get(u.username).flatMap(_.headOption).getOrElse("")
        ConvergenceUserInfo(u, globalRole)
      }
    }).map(GetConvergenceUserResponse)

    reply(convergenceUser)
  }

  private[this] def onGetConvergenceUsers(message: GetConvergenceUsersRequest): Unit = {
    val GetConvergenceUsersRequest(filter, limit, offset) = message
    val convergenceUsers = (for {
      users <- userStore.getUsers(filter, limit, offset)
      roles <- roleStore.getRolesForUsersAndTarget(users.map(_.username).toSet, ServerRoleTarget())
    } yield {
      users.map { user =>
        val globalRole = roles.get(user.username).flatMap(_.headOption).getOrElse("")
        ConvergenceUserInfo(user, globalRole)
      }.toSet
    }).map(GetConvergenceUsersResponse)

    reply(convergenceUsers)
  }

  private[this] def onDeleteConvergenceUser(message: DeleteConvergenceUserRequest): Unit = {
    val DeleteConvergenceUserRequest(username) = message
    val result = (domainStoreActor ? DeleteDomainsForUserRequest(username))
      .mapTo[Unit]
      .flatMap(_ => FutureUtils.tryToFuture(userStore.deleteUser(username)))
    reply(result)
  }

  private[this] def onUpdateConvergenceUserProfile(message: UpdateConvergenceUserProfileRequest): Unit = {
    val UpdateConvergenceUserProfileRequest(username, email, firstName, lastName, displayName) = message
    val update = User(username, email, firstName, lastName, displayName, None)
    reply(userStore.updateUser(update))
  }

  private[this] def onUpdateConvergenceUser(message: UpdateConvergenceUserRequest): Unit = {
    val UpdateConvergenceUserRequest(username, email, firstName, lastName, displayName, globalRole) = message
    val update = User(username, email, firstName, lastName, displayName, None)
    reply(userStore.updateUser(update))
  }

  private[this] def onSetUserPassword(message: SetPasswordRequest): Unit = {
    val SetPasswordRequest(username, password) = message
    reply(userStore.setUserPassword(username, password))
  }

  private[this] def onGetUserBearerToken(message: GetUserBearerTokenRequest): Unit = {
    val GetUserBearerTokenRequest(username) = message
    val result = userStore.getBearerToken(username).map(GetUserBearerTokenResponse)
    reply(result)
  }

  private[this] def onRegenerateUserBearerToken(message: RegenerateUserBearerTokenRequest): Unit = {
    val RegenerateUserBearerTokenRequest(username) = message
    val bearerToken = userCreator.bearerTokenGen.nextString()
    reply(userStore.setBearerToken(username, bearerToken).map(_ => bearerToken))
  }
}


object ConvergenceUserManagerActor {
  val RelativePath = "ConvergenceUserManagerActor"

  def props(dbProvider: DatabaseProvider, domainStoreActor: ActorRef): Props =
    Props(new ConvergenceUserManagerActor(dbProvider, domainStoreActor))

  case class ConvergenceUserInfo(user: User, globalRole: String)

  sealed trait ConvergenceUserManagerActorMessage extends CborSerializable

  case class CreateConvergenceUserRequest(username: String,
                                          email: String,
                                          firstName: String,
                                          lastName: String,
                                          displayName: String,
                                          password: String,
                                          globalRole: String) extends ConvergenceUserManagerActorMessage

  case class UpdateConvergenceUserRequest(username: String,
                                          email: String,
                                          firstName: String,
                                          lastName: String,
                                          displayName: String,
                                          globalRole: String) extends ConvergenceUserManagerActorMessage

  case class UpdateConvergenceUserProfileRequest(username: String,
                                                 email: String,
                                                 firstName: String,
                                                 lastName: String,
                                                 displayName: String) extends ConvergenceUserManagerActorMessage

  case class SetPasswordRequest(username: String, password: String) extends ConvergenceUserManagerActorMessage

  case class DeleteConvergenceUserRequest(username: String) extends ConvergenceUserManagerActorMessage

  case class GetConvergenceUsersRequest(filter: Option[String], limit: Option[Int], offset: Option[Int]) extends ConvergenceUserManagerActorMessage

  case class GetConvergenceUsersResponse(users: Set[ConvergenceUserInfo]) extends CborSerializable

  case class GetConvergenceUserRequest(username: String) extends ConvergenceUserManagerActorMessage

  case class GetConvergenceUserResponse(user: Option[ConvergenceUserInfo]) extends CborSerializable

  case class GetUserBearerTokenRequest(username: String) extends ConvergenceUserManagerActorMessage

  case class GetUserBearerTokenResponse(token: Option[String]) extends CborSerializable

  case class RegenerateUserBearerTokenRequest(username: String) extends ConvergenceUserManagerActorMessage

  case class RegenerateUserBearerTokenResponse(username: String) extends CborSerializable

}
