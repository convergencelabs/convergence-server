/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.domain.DomainId

import akka.actor.ActorLogging
import akka.actor.Props
import akka.util.Timeout

object UserFavoriteDomainStoreActor {
  val RelativePath = "UserFavoriteDomainStoreActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new UserFavoriteDomainStoreActor(dbProvider))

  case class AddFavoriteDomain(username: String, domain: DomainId)
  case class RemoveFavoriteDomain(username: String, domain: DomainId)
  case class GetFavoritesForUser(username: String)
}

class UserFavoriteDomainStoreActor private[datastore] (private[this] val dbProvider: DatabaseProvider) extends StoreActor
  with ActorLogging {

  import UserFavoriteDomainStoreActor._

  // FIXME: Read this from configuration
  private[this] implicit val requstTimeout = Timeout(2 seconds)
  private[this] implicit val exectionContext = context.dispatcher

  private[this] val favoriteStore = new UserFavoriteDomainStore(dbProvider)

  def receive: Receive = {
    case message: AddFavoriteDomain => 
      addFavorite(message)
    case message: RemoveFavoriteDomain => 
      removeFavorite(message)
    case message: GetFavoritesForUser => 
      getFavoritesForUser(message)
    case message: Any => unhandled(message)
  }

  def addFavorite(message: AddFavoriteDomain): Unit = {
    val AddFavoriteDomain(username, domain) = message
    reply(favoriteStore.addFavorite(username, domain))
  }

  def removeFavorite(message: RemoveFavoriteDomain): Unit = {
    val RemoveFavoriteDomain(username, domain) = message
    reply(favoriteStore.removeFavorite(username, domain))
  }
  
  def getFavoritesForUser(message: GetFavoritesForUser): Unit = {
    val GetFavoritesForUser(username) = message
    reply(favoriteStore.getFavoritesForUser(username))
  }
}
