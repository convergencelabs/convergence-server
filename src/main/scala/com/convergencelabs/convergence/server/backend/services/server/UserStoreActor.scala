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

import java.util.concurrent.TimeUnit

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.backend.datastore.convergence.{RoleStore, UserStore}
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException, InvalidValueException}
import com.convergencelabs.convergence.server.model.server.role.ServerRoleTarget
import com.convergencelabs.convergence.server.model.server.user.User
import com.convergencelabs.convergence.server.util.concurrent.FutureUtils
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

private final class UserStoreActor(context: ActorContext[UserStoreActor.Message],
                                   userStore: UserStore,
                                   roleStore: RoleStore,
                                   userCreator: UserCreator,
                                   domainStoreActor: ActorRef[DomainStoreActor.Message])
  extends AbstractBehavior[UserStoreActor.Message](context) {

  import UserStoreActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

  private[this] implicit val executionContext: ExecutionContextExecutor = context.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system

  private[this] val domainDeletionTimeout = Timeout(system.settings.config.getDuration(
    "convergence.user-manager.domain-deletion-timeout", TimeUnit.MILLISECONDS), TimeUnit.MICROSECONDS)

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
    userCreator
      .createUser(user, password, serverRole)
      .map(_ => CreateConvergenceUserResponse(Right(Ok())))
      .recover {
        case InvalidValueException(_, message, _) =>
          CreateConvergenceUserResponse(Left(InvalidValueError(message)))
        case DuplicateValueException(field, _, _) =>
          CreateConvergenceUserResponse(Left(UserAlreadyExistsError(field)))
        case _ =>
          CreateConvergenceUserResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
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
    })
      .map(_.map(user => GetConvergenceUserResponse(Right(user))).getOrElse(GetConvergenceUserResponse(Left(UserNotFoundError()))))
      .recover {
        case _: EntityNotFoundException =>
          GetConvergenceUserResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("Unexpected error getting convergence user", cause)
          GetConvergenceUserResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetConvergenceUsers(message: GetConvergenceUsersRequest): Unit = {
    val GetConvergenceUsersRequest(filter, offset, limit, replyTo) = message
    (for {
      users <- userStore.getUsers(filter, offset, limit)
      roles <- roleStore.getRolesForUsersAndTarget(users.map(_.username).toSet, ServerRoleTarget())
    } yield {
      users.map { user =>
        val globalRole = roles.get(user.username).flatMap(_.headOption).getOrElse("")
        ConvergenceUserInfo(user, globalRole)
      }.toSet
    })
      .map(users => GetConvergenceUsersResponse(Right(users)))
      .recover { cause =>
        context.log.error("Unexpected error getting convergence user", cause)
        GetConvergenceUsersResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onDeleteConvergenceUser(message: DeleteConvergenceUserRequest): Unit = {
    implicit val requestTimeout: Timeout = domainDeletionTimeout
    val DeleteConvergenceUserRequest(username, replyTo) = message
    domainStoreActor
      .ask[DomainStoreActor.DeleteDomainsForUserResponse](ref => DomainStoreActor.DeleteDomainsForUserRequest(username, ref))
      .flatMap(_ => FutureUtils.tryToFuture(userStore.deleteUser(username)))
      .map(_ => DeleteConvergenceUserResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          DeleteConvergenceUserResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("Unexpected error deleting convergence user", cause)
          DeleteConvergenceUserResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onUpdateConvergenceUserProfile(message: UpdateConvergenceUserProfileRequest): Unit = {
    val UpdateConvergenceUserProfileRequest(username, email, firstName, lastName, displayName, replyTo) = message
    val update = User(username, email, firstName, lastName, displayName, None)
    userStore
      .updateUser(update)
      .map(_ => UpdateConvergenceUserProfileResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          UpdateConvergenceUserProfileResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("Unexpected error updating convergence user profile", cause)
          UpdateConvergenceUserProfileResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onUpdateConvergenceUser(message: UpdateConvergenceUserRequest): Unit = {
    val UpdateConvergenceUserRequest(username, email, firstName, lastName, displayName, globalRole, replyTo) = message
    val update = User(username, email, firstName, lastName, displayName, None)
    (for {
      - <- userStore.updateUser(update)
      _ <- roleStore.setUserRolesForTarget(username, ServerRoleTarget(), Set(globalRole))
    } yield ())
      .map(_ => UpdateConvergenceUserResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          UpdateConvergenceUserResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("Unexpected error updating convergence user profile", cause)
          UpdateConvergenceUserResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onSetUserPassword(message: SetPasswordRequest): Unit = {
    val SetPasswordRequest(username, password, replyTo) = message
    userStore
      .setUserPassword(username, password)
      .map(_ => SetPasswordResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          SetPasswordResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("Unexpected error setting user password", cause)
          SetPasswordResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetUserBearerToken(message: GetUserBearerTokenRequest): Unit = {
    val GetUserBearerTokenRequest(username, replyTo) = message
    userStore
      .getBearerToken(username)
      .map(_.map(token => GetUserBearerTokenResponse(Right(token))).getOrElse(GetUserBearerTokenResponse(Left(UserNotFoundError()))))
      .recover { cause =>
        context.log.error("Unexpected error getting user bearer token", cause)
        GetUserBearerTokenResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onRegenerateUserBearerToken(message: RegenerateUserBearerTokenRequest): Unit = {
    val RegenerateUserBearerTokenRequest(username, replyTo) = message
    val bearerToken = userCreator.bearerTokenGen.nextString()
    userStore
      .setBearerToken(username, bearerToken)
      .map(_ => RegenerateUserBearerTokenResponse(Right(bearerToken)))
      .recover {
        case _: EntityNotFoundException =>
          RegenerateUserBearerTokenResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("Unexpected error setting user bearer token", cause)
          RegenerateUserBearerTokenResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }
}


object UserStoreActor {
  val Key: ServiceKey[Message] = ServiceKey[Message]("ConvergenceUserManagerActor")

  def apply(userStore: UserStore,
            roleStore: RoleStore,
            userCreator: UserCreator,
            domainStoreActor: ActorRef[DomainStoreActor.Message]): Behavior[Message] =
    Behaviors.setup(context => new UserStoreActor(context, userStore, roleStore, userCreator, domainStoreActor))

  final case class ConvergenceUserInfo(user: User, globalRole: String)


  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable


  //
  // CreateConvergenceUser
  //
  final case class CreateConvergenceUserRequest(username: String,
                                                email: String,
                                                firstName: String,
                                                lastName: String,
                                                displayName: String,
                                                password: String,
                                                globalRole: String,
                                                replyTo: ActorRef[CreateConvergenceUserResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserAlreadyExistsError], name = "user_exists"),
    new JsonSubTypes.Type(value = classOf[InvalidValueError], name = "invalid"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait CreateConvergenceUserError

  final case class UserAlreadyExistsError(filed: String) extends CreateConvergenceUserError

  final case class CreateConvergenceUserResponse(response: Either[CreateConvergenceUserError, Ok]) extends CborSerializable

  //
  //UpdateConvergenceUser
  //
  final case class UpdateConvergenceUserRequest(username: String,
                                                email: String,
                                                firstName: String,
                                                lastName: String,
                                                displayName: String,
                                                globalRole: String,
                                                replyTo: ActorRef[UpdateConvergenceUserResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait UpdateConvergenceUserError

  final case class UpdateConvergenceUserResponse(response: Either[UpdateConvergenceUserError, Ok]) extends CborSerializable

  //
  // UpdateConvergenceUserProfile
  //
  final case class UpdateConvergenceUserProfileRequest(username: String,
                                                       email: String,
                                                       firstName: String,
                                                       lastName: String,
                                                       displayName: String,
                                                       replyTo: ActorRef[UpdateConvergenceUserProfileResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait UpdateConvergenceUserProfileError

  final case class UpdateConvergenceUserProfileResponse(response: Either[UpdateConvergenceUserProfileError, Ok]) extends CborSerializable

  //
  // SetPassword
  //
  final case class SetPasswordRequest(username: String, password: String, replyTo: ActorRef[SetPasswordResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetPasswordError

  final case class SetPasswordResponse(response: Either[SetPasswordError, Ok]) extends CborSerializable

  //
  // DeleteConvergenceUser
  //
  final case class DeleteConvergenceUserRequest(username: String, replyTo: ActorRef[DeleteConvergenceUserResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait DeleteConvergenceUserError

  final case class DeleteConvergenceUserResponse(response: Either[DeleteConvergenceUserError, Ok]) extends CborSerializable

  //
  // GetConvergenceUsers
  //
  final case class GetConvergenceUsersRequest(filter: Option[String],
                                              @JsonDeserialize(contentAs = classOf[Long])
                                              offset: QueryOffset,
                                              @JsonDeserialize(contentAs = classOf[Long])
                                              limit: QueryLimit,
                                              replyTo: ActorRef[GetConvergenceUsersResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetConvergenceUsersError

  final case class GetConvergenceUsersResponse(users: Either[GetConvergenceUsersError, Set[ConvergenceUserInfo]]) extends CborSerializable

  //
  // GetConvergenceUser
  //
  final case class GetConvergenceUserRequest(username: String, replyTo: ActorRef[GetConvergenceUserResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetConvergenceUserError

  final case class GetConvergenceUserResponse(user: Either[GetConvergenceUserError, ConvergenceUserInfo]) extends CborSerializable

  //
  // GetUserBearerToken
  //
  final case class GetUserBearerTokenRequest(username: String, replyTo: ActorRef[GetUserBearerTokenResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUserBearerTokenError

  final case class GetUserBearerTokenResponse(token: Either[GetUserBearerTokenError, String]) extends CborSerializable

  //
  // RegenerateUserBearerToken
  //
  final case class RegenerateUserBearerTokenRequest(username: String, replyTo: ActorRef[RegenerateUserBearerTokenResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait RegenerateUserBearerTokenError

  final case class RegenerateUserBearerTokenResponse(token: Either[RegenerateUserBearerTokenError, String]) extends CborSerializable

  //
  // Common Errors
  //

  final case class InvalidValueError(field: String) extends AnyRef
    with CreateConvergenceUserError

  final case class UserNotFoundError() extends AnyRef
    with SetPasswordError
    with DeleteConvergenceUserError
    with UpdateConvergenceUserProfileError
    with UpdateConvergenceUserError
    with GetConvergenceUserError
    with GetUserBearerTokenError
    with RegenerateUserBearerTokenError

  final case class UnknownError() extends AnyRef
    with SetPasswordError
    with DeleteConvergenceUserError
    with UpdateConvergenceUserProfileError
    with UpdateConvergenceUserError
    with CreateConvergenceUserError
    with GetConvergenceUsersError
    with GetConvergenceUserError
    with GetUserBearerTokenError
    with RegenerateUserBearerTokenError

}
