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
import com.convergencelabs.convergence.server.util.RandomStringGenerator

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps


class UserApiKeyStoreActor private[datastore](private[this] val dbProvider: DatabaseProvider) extends StoreActor
  with ActorLogging {

  import UserApiKeyStoreActor._

  // FIXME: Read this from configuration
  private[this] implicit val requestTimeout: Timeout = Timeout(2 seconds)
  private[this] implicit val executionContext: ExecutionContextExecutor = context.dispatcher

  private[this] val userApiKeyStore: UserApiKeyStore = new UserApiKeyStore(dbProvider)
  private[this] val keyGen = new RandomStringGenerator(length = 64, RandomStringGenerator.AlphaNumeric)

  def receive: Receive = {
    case message: GetApiKeysForUserRequest =>
      onGetKeys(message)
    case message: GetApiKeyRequest =>
      onGetKey(message)
    case message: CreateApiKeyRequest =>
      onCreateKey(message)
    case message: DeleteApiKeyRequest =>
      onDeleteKey(message)
    case message: UpdateKeyRequest =>
      onUpdateKey(message)
    case message: Any =>
      unhandled(message)
  }

  private[this] def onGetKeys(message: GetApiKeysForUserRequest): Unit = {
    val GetApiKeysForUserRequest(username) = message
    reply(userApiKeyStore.getKeysForUser(username).map(GetApiKeysForUserResponse))
  }

  private[this] def onGetKey(message: GetApiKeyRequest): Unit = {
    val GetApiKeyRequest(username, key) = message
    reply(userApiKeyStore.getKeyForUser(username, key).map(GetApiKeyResponse))
  }

  private[this] def onCreateKey(message: CreateApiKeyRequest): Unit = {
    val CreateApiKeyRequest(username, keyName, enabled) = message
    val key = UserApiKey(username, keyName, keyGen.nextString(), enabled.getOrElse(true), None)
    reply(userApiKeyStore
      .createKey(key)
      .map(_ => key))
  }

  private[this] def onDeleteKey(message: DeleteApiKeyRequest): Unit = {
    val DeleteApiKeyRequest(username, key) = message
    reply(userApiKeyStore.deleteKey(key, username))
  }

  private[this] def onUpdateKey(message: UpdateKeyRequest): Unit = {
    val UpdateKeyRequest(username, key, name, enabled) = message
    reply(userApiKeyStore.updateKeyKey(key, username, name, enabled))
  }
}


object UserApiKeyStoreActor {
  val RelativePath = "UserApiKeyStoreActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new UserApiKeyStoreActor(dbProvider))

  trait UserApiKeyStoreActorRequest extends CborSerializable
  case class GetApiKeysForUserRequest(username: String) extends UserApiKeyStoreActorRequest

  case class GetApiKeysForUserResponse(keys: Set[UserApiKey])

  case class GetApiKeyRequest(username: String, key: String)  extends UserApiKeyStoreActorRequest
  case class GetApiKeyResponse(key: Option[UserApiKey])

  case class CreateApiKeyRequest(username: String, name: String, enabled: Option[Boolean])  extends UserApiKeyStoreActorRequest

  case class DeleteApiKeyRequest(username: String, key: String)  extends UserApiKeyStoreActorRequest

  case class UpdateKeyRequest(username: String, key: String, name: String, enabled: Boolean)  extends UserApiKeyStoreActorRequest

}
