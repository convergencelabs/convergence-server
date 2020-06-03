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
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.{Domain, DomainId}
import grizzled.slf4j.Logging

import scala.language.postfixOps
import scala.util.{Failure, Success}


private class UserFavoriteDomainStoreActor private[datastore](private[this] val context: ActorContext[UserFavoriteDomainStoreActor.Message],
                                                      private[this] val dbProvider: DatabaseProvider)
  extends AbstractBehavior[UserFavoriteDomainStoreActor.Message](context) with Logging {

  import UserFavoriteDomainStoreActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

  private[this] val favoriteStore = new UserFavoriteDomainStore(dbProvider)

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
    favoriteStore.addFavorite(username, domain) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onRemoveFavorite(message: RemoveFavoriteDomainRequest): Unit = {
    val RemoveFavoriteDomainRequest(username, domain, replyTo) = message
    favoriteStore.removeFavorite(username, domain) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetFavoritesForUser(message: GetFavoritesForUserRequest): Unit = {
    val GetFavoritesForUserRequest(username, replyTo) = message
    favoriteStore.getFavoritesForUser(username) match {
      case Success(favorites) =>
        replyTo ! GetFavoritesForUserSuccess(favorites)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }
}

object UserFavoriteDomainStoreActor {

  val Key: ServiceKey[Message] = ServiceKey[Message]("UserFavoriteDomainStore")

  def apply(dbProvider: DatabaseProvider): Behavior[Message] =
    Behaviors.setup(context => new UserFavoriteDomainStoreActor(context, dbProvider))

  sealed trait Message extends CborSerializable

  //
  // RemoveFavoriteDomain
  //
  case class AddFavoriteDomainRequest(username: String, domain: DomainId, replyTo: ActorRef[AddFavoriteDomainResponse]) extends Message

  sealed trait AddFavoriteDomainResponse extends CborSerializable

  //
  // RemoveFavoriteDomain
  //
  case class RemoveFavoriteDomainRequest(username: String, domain: DomainId, replyTo: ActorRef[RemoveFavoriteDomainResponse]) extends Message

  sealed trait RemoveFavoriteDomainResponse extends CborSerializable

  //
  // GetFavoritesForUser
  //
  case class GetFavoritesForUserRequest(username: String, replyTo: ActorRef[GetFavoritesForUserResponse]) extends Message

  sealed trait GetFavoritesForUserResponse extends CborSerializable

  case class GetFavoritesForUserSuccess(domains: Set[Domain]) extends GetFavoritesForUserResponse

  //
  // Generic Responses
  //
  case class RequestFailure(cause: Throwable) extends CborSerializable
    with AddFavoriteDomainResponse
    with RemoveFavoriteDomainResponse
    with GetFavoritesForUserResponse


  case class RequestSuccess() extends CborSerializable
    with AddFavoriteDomainResponse
    with RemoveFavoriteDomainResponse
}
