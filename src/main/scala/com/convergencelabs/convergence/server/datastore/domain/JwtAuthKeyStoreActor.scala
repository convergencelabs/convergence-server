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
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.datastore.domain.JwtAuthKeyStore.KeyInfo
import com.convergencelabs.convergence.server.domain.JwtAuthKey


class JwtAuthKeyStoreActor private[datastore] (private[this] val keyStore: JwtAuthKeyStore)
    extends StoreActor with ActorLogging {
  
  import JwtAuthKeyStoreActor._

  def receive: Receive = {
    case GetJwtAuthKeysRequest(offset, limit) =>
      onGetKeys(offset, limit)
    case GetJwtAuthKeyRequest(id) =>
      onGetKey(id)
    case DeleteDomainJwtAuthKey(id) =>
      onDeleteKey(id)
    case CreateDomainJwtAuthKey(key) =>
      onCreateKey(key)
    case UpdateDomainJwtAuthKey(key) =>
      onUpdateKey(key)
    case message: Any =>
      unhandled(message)
  }

  private[this] def onGetKeys(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(keyStore.getKeys(offset, limit).map(GetJwtAuthKeysResponse))
  }

  private[this] def onGetKey(id: String): Unit = {
    reply(keyStore.getKey(id).map(GetJwtAuthKeyResponse))
  }

  private[this] def onDeleteKey(id: String): Unit = {
    reply(keyStore.deleteKey(id))
  }

  private[this] def onCreateKey(key: KeyInfo): Unit = {
    reply(keyStore.createKey(key))
  }

  private[this] def onUpdateKey(key: KeyInfo): Unit = {
    reply(keyStore.updateKey(key))
  }
}


object JwtAuthKeyStoreActor {
  def props(keyStore: JwtAuthKeyStore): Props = Props(new JwtAuthKeyStoreActor(keyStore))

  sealed trait JwtAuthKeyStoreRequest extends CborSerializable

  case class GetJwtAuthKeysRequest(offset: Option[Int], limit: Option[Int]) extends JwtAuthKeyStoreRequest
  case class GetJwtAuthKeysResponse(keys: List[JwtAuthKey])  extends CborSerializable

  case class GetJwtAuthKeyRequest(id: String) extends JwtAuthKeyStoreRequest
  case class GetJwtAuthKeyResponse(key: Option[JwtAuthKey]) extends CborSerializable

  case class DeleteDomainJwtAuthKey(id: String) extends JwtAuthKeyStoreRequest
  case class UpdateDomainJwtAuthKey(key: KeyInfo) extends JwtAuthKeyStoreRequest
  case class CreateDomainJwtAuthKey(key: KeyInfo) extends JwtAuthKeyStoreRequest

}
