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

package com.convergencelabs.convergence.server.backend.datastore.convergence

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.backend.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.backend.services.domain.Domain
import com.convergencelabs.convergence.server.model.domain.DomainId
import com.fasterxml.jackson.annotation.JsonSubTypes

import scala.language.postfixOps


class UserFavoriteDomainStoreActor private(context: ActorContext[UserFavoriteDomainStoreActor.Message],
                                           favoriteStore: UserFavoriteDomainStore)
  extends AbstractBehavior[UserFavoriteDomainStoreActor.Message](context) {

  import UserFavoriteDomainStoreActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case message: AddFavoriteDomainRequest =>
        onAddFavorite(message)
      case message: RemoveFavoriteDomainRequest =>
        onRemoveFavorite(message)
      case message: GetFavoritesForUserRequest =>
        onGetFavoritesForUser(message)
    }
    Behaviors.same
  }


  private[this] def onAddFavorite(message: AddFavoriteDomainRequest): Unit = {
    val AddFavoriteDomainRequest(username, domain, replyTo) = message
    favoriteStore
      .addFavorite(username, domain)
      .map(_ => AddFavoriteDomainResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          AddFavoriteDomainResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("unexpected error adding favorite domains for user", cause)
          AddFavoriteDomainResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onRemoveFavorite(message: RemoveFavoriteDomainRequest): Unit = {
    val RemoveFavoriteDomainRequest(username, domain, replyTo) = message
    favoriteStore
      .removeFavorite(username, domain)
      .map(_ => RemoveFavoriteDomainResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          RemoveFavoriteDomainResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("unexpected error removing favorite domains for user", cause)
          RemoveFavoriteDomainResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetFavoritesForUser(message: GetFavoritesForUserRequest): Unit = {
    val GetFavoritesForUserRequest(username, replyTo) = message
    favoriteStore
      .getFavoritesForUser(username)
      .map(domains => GetFavoritesForUserResponse(Right(domains)))
      .recover {
        case _: EntityNotFoundException =>
          GetFavoritesForUserResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("unexpected error getting favorite domains for user", cause)
          GetFavoritesForUserResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }
}

object UserFavoriteDomainStoreActor {

  val Key: ServiceKey[Message] = ServiceKey[Message]("UserFavoriteDomainStore")

  def apply(favoriteStore: UserFavoriteDomainStore): Behavior[Message] =
    Behaviors.setup(context => new UserFavoriteDomainStoreActor(context, favoriteStore))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  //
  // RemoveFavoriteDomain
  //
  final case class AddFavoriteDomainRequest(username: String,
                                            domain: DomainId,
                                            replyTo: ActorRef[AddFavoriteDomainResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait AddFavoriteDomainError

  final case class AddFavoriteDomainResponse(response: Either[AddFavoriteDomainError, Ok]) extends CborSerializable

  //
  // RemoveFavoriteDomain
  //
  final case class RemoveFavoriteDomainRequest(username: String, domain: DomainId, replyTo: ActorRef[RemoveFavoriteDomainResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait RemoveFavoriteDomainError

  final case class RemoveFavoriteDomainResponse(response: Either[RemoveFavoriteDomainError, Ok]) extends CborSerializable

  //
  // GetFavoritesForUser
  //
  final case class GetFavoritesForUserRequest(username: String, replyTo: ActorRef[GetFavoritesForUserResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetFavoritesForUserError

  final case class GetFavoritesForUserResponse(domains: Either[GetFavoritesForUserError, Set[Domain]]) extends CborSerializable

  //
  // Commons Errors
  //
  final case class UserNotFoundError() extends AnyRef
    with GetFavoritesForUserError
    with RemoveFavoriteDomainError
    with AddFavoriteDomainError

  final case class UnknownError() extends AnyRef
    with GetFavoritesForUserError
    with RemoveFavoriteDomainError
    with AddFavoriteDomainError

}
