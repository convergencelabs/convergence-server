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
import com.convergencelabs.convergence.server.util.RandomStringGenerator
import grizzled.slf4j.Logging

import scala.util.{Failure, Success}


private class UserApiKeyStoreActor private[datastore](private[this] val context: ActorContext[UserApiKeyStoreActor.Message],
                                                      private[this] val dbProvider: DatabaseProvider)
  extends AbstractBehavior[UserApiKeyStoreActor.Message](context) with Logging {

  import UserApiKeyStoreActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

  private[this] val userApiKeyStore: UserApiKeyStore = new UserApiKeyStore(dbProvider)
  private[this] val keyGen = new RandomStringGenerator(length = 64, RandomStringGenerator.AlphaNumeric)

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case message: GetApiKeysForUserRequest =>
        onGetKeys(message)
      case message: GetUserApiKeyRequest =>
        onGetKey(message)
      case message: CreateUserApiKeyRequest =>
        onCreateKey(message)
      case message: DeleteUserApiKeyRequest =>
        onDeleteKey(message)
      case message: UpdateUserApiKeyRequest =>
        onUpdateKey(message)
    }
    Behaviors.same
  }

  private[this] def onGetKeys(message: GetApiKeysForUserRequest): Unit = {
    val GetApiKeysForUserRequest(username, replyTo) = message
    userApiKeyStore.getKeysForUser(username) match {
      case Success(keys) =>
        replyTo ! GetApiKeysForUserSuccess(keys)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetKey(message: GetUserApiKeyRequest): Unit = {
    val GetUserApiKeyRequest(username, key, replyTo) = message
    userApiKeyStore.getKeyForUser(username, key) match {
      case Success(key) =>
        replyTo ! GetUserApiKeySuccess(key)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onCreateKey(message: CreateUserApiKeyRequest): Unit = {
    val CreateUserApiKeyRequest(username, keyName, enabled, replyTo) = message
    val key = UserApiKey(username, keyName, keyGen.nextString(), enabled.getOrElse(true), None)
    userApiKeyStore
      .createKey(key)
      .map(_ => key) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onDeleteKey(message: DeleteUserApiKeyRequest): Unit = {
    val DeleteUserApiKeyRequest(username, key, replyTo) = message
    userApiKeyStore.deleteKey(key, username) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onUpdateKey(message: UpdateUserApiKeyRequest): Unit = {
    val UpdateUserApiKeyRequest(username, key, name, enabled, replyTo) = message
    userApiKeyStore.updateKeyKey(key, username, name, enabled) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }
}


object UserApiKeyStoreActor {

  val Key: ServiceKey[Message] = ServiceKey[Message]("UserApiKeyStore")

  def apply(dbProvider: DatabaseProvider): Behavior[Message] =
    Behaviors.setup(context => new UserApiKeyStoreActor(context, dbProvider))


  trait Message extends CborSerializable

  //
  // Generic Responses
  //
  case class GetApiKeysForUserRequest(username: String, replyTo: ActorRef[GetApiKeysForUserResponse]) extends Message

  trait GetApiKeysForUserResponse extends CborSerializable

  case class GetApiKeysForUserSuccess(keys: Set[UserApiKey]) extends GetApiKeysForUserResponse

  //
  // GetUserApiKey
  //
  case class GetUserApiKeyRequest(username: String, key: String, replyTo: ActorRef[GetUserApiKeyResponse]) extends Message

  trait GetUserApiKeyResponse extends CborSerializable

  case class GetUserApiKeySuccess(key: Option[UserApiKey]) extends GetUserApiKeyResponse

  //
  // CreateUserApiKey
  //
  case class CreateUserApiKeyRequest(username: String, name: String, enabled: Option[Boolean], replyTo: ActorRef[CreateUserApiKeyResponse]) extends Message

  trait CreateUserApiKeyResponse extends CborSerializable

  //
  // DeleteUserApiKey
  //
  case class DeleteUserApiKeyRequest(username: String, key: String, replyTo: ActorRef[DeleteUserApiKeyResponse]) extends Message

  trait DeleteUserApiKeyResponse extends CborSerializable

  //
  // UpdateUserApiKey
  //
  case class UpdateUserApiKeyRequest(username: String, key: String, name: String, enabled: Boolean, replyTo: ActorRef[UpdateUserApiKeyResponse]) extends Message

  trait UpdateUserApiKeyResponse extends CborSerializable

  //
  // Generic Responses
  //
  case class RequestFailure(cause: Throwable) extends CborSerializable
    with GetApiKeysForUserResponse
    with GetUserApiKeyResponse
    with CreateUserApiKeyResponse
    with DeleteUserApiKeyResponse
    with UpdateUserApiKeyResponse


  case class RequestSuccess() extends CborSerializable
    with CreateUserApiKeyResponse
    with DeleteUserApiKeyResponse
    with UpdateUserApiKeyResponse

}
