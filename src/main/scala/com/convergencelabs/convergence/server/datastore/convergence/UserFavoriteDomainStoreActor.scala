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

import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.{Domain, DomainId}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps


class UserFavoriteDomainStoreActor private[datastore](private[this] val dbProvider: DatabaseProvider) extends StoreActor
  with ActorLogging {

  import UserFavoriteDomainStoreActor._

  // FIXME: Read this from configuration
  private[this] implicit val requestTimeout: Timeout = Timeout(2 seconds)
  private[this] implicit val executionContext: ExecutionContextExecutor = context.dispatcher

  private[this] val favoriteStore = new UserFavoriteDomainStore(dbProvider)

  def receive: Receive = {
    case message: AddFavoriteDomainRequest =>
      onAddFavorite(message)
    case message: RemoveFavoriteDomainRequest =>
      onRemoveFavorite(message)
    case message: GetFavoritesForUserRequest =>
      onGetFavoritesForUser(message)
    case message: Any => unhandled(message)
  }

  private[this] def onAddFavorite(message: AddFavoriteDomainRequest): Unit = {
    val AddFavoriteDomainRequest(username, domain) = message
    reply(favoriteStore.addFavorite(username, domain))
  }

  private[this] def onRemoveFavorite(message: RemoveFavoriteDomainRequest): Unit = {
    val RemoveFavoriteDomainRequest(username, domain) = message
    reply(favoriteStore.removeFavorite(username, domain))
  }

  private[this] def onGetFavoritesForUser(message: GetFavoritesForUserRequest): Unit = {
    val GetFavoritesForUserRequest(username) = message
    reply(favoriteStore.getFavoritesForUser(username).map(GetFavoritesForUserResponse))
  }
}

object UserFavoriteDomainStoreActor {
  val RelativePath = "UserFavoriteDomainStoreActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new UserFavoriteDomainStoreActor(dbProvider))

  sealed trait UserFavoriteDomainStoreActorMessage extends CborSerializable

  case class AddFavoriteDomainRequest(username: String, domain: DomainId) extends UserFavoriteDomainStoreActorMessage

  case class RemoveFavoriteDomainRequest(username: String, domain: DomainId) extends UserFavoriteDomainStoreActorMessage

  case class GetFavoritesForUserRequest(username: String) extends UserFavoriteDomainStoreActorMessage

  case class GetFavoritesForUserResponse(domains: Set[Domain]) extends UserFavoriteDomainStoreActorMessage

}
