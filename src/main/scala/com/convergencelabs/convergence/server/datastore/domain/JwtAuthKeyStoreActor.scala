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

package com.convergencelabs.convergence.server.datastore.domain

import akka.actor.{ActorLogging, Props}
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.datastore.domain.JwtAuthKeyStore.KeyInfo

object JwtAuthKeyStoreActor {
  def props(keyStore: JwtAuthKeyStore): Props = Props(new JwtAuthKeyStoreActor(keyStore))

  sealed trait ApiKeyStoreRequest
  case class GetDomainApiKeys(offset: Option[Int], limit: Option[Int]) extends ApiKeyStoreRequest
  case class GetDomainApiKey(id: String) extends ApiKeyStoreRequest
  case class DeleteDomainApiKey(id: String) extends ApiKeyStoreRequest
  case class UpdateDomainApiKey(key: KeyInfo) extends ApiKeyStoreRequest
  case class CreateDomainApiKey(key: KeyInfo) extends ApiKeyStoreRequest
}

class JwtAuthKeyStoreActor private[datastore] (private[this] val keyStore: JwtAuthKeyStore)
    extends StoreActor with ActorLogging {
  
  import JwtAuthKeyStoreActor._

  def receive: Receive = {
    case GetDomainApiKeys(offset, limit) => getKeys(offset, limit)
    case GetDomainApiKey(id) => getKey(id)
    case DeleteDomainApiKey(id) => deleteKey(id)
    case CreateDomainApiKey(key) => createKey(key)
    case UpdateDomainApiKey(key) => updateKey(key)
    case message: Any => unhandled(message)
  }

  def getKeys(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(keyStore.getKeys(offset, limit))
  }

  def getKey(id: String): Unit = {
    reply(keyStore.getKey(id))
  }

  def deleteKey(id: String): Unit = {
    reply(keyStore.deleteKey(id))
  }

  def createKey(key: KeyInfo): Unit = {
    reply(keyStore.createKey(key))
  }

  def updateKey(key: KeyInfo): Unit = {
    reply(keyStore.updateKey(key))
  }
}
