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
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.util.RandomStringGenerator
import com.fasterxml.jackson.annotation.JsonSubTypes


class UserApiKeyStoreActor private(context: ActorContext[UserApiKeyStoreActor.Message],
                                   userApiKeyStore: UserApiKeyStore)
  extends AbstractBehavior[UserApiKeyStoreActor.Message](context) {

  import UserApiKeyStoreActor._

  private[this] val keyGen = new RandomStringGenerator(length = 64, RandomStringGenerator.AlphaNumeric)

  context.system.receptionist ! Receptionist.Register(Key, context.self)

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
    userApiKeyStore
      .getKeysForUser(username)
      .map(keys => GetApiKeysForUserResponse(Right(keys)))
      .recover {
        case _: EntityNotFoundException =>
          GetApiKeysForUserResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("Unexpected error updating user api key", cause)
          GetApiKeysForUserResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetKey(message: GetUserApiKeyRequest): Unit = {
    val GetUserApiKeyRequest(username, key, replyTo) = message
    userApiKeyStore
      .getKeyForUser(username, key)
      .map(_.map(key => GetUserApiKeyResponse(Right(key)))
        .getOrElse(GetUserApiKeyResponse(Left(KeyNotFoundError())))
      )
      .recover { cause =>
        context.log.error("Unexpected error getting user api key", cause)
        GetUserApiKeyResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onCreateKey(message: CreateUserApiKeyRequest): Unit = {
    val CreateUserApiKeyRequest(username, keyName, enabled, replyTo) = message
    val key = UserApiKey(username, keyName, keyGen.nextString(), enabled.getOrElse(true), None)
    userApiKeyStore
      .createKey(key)
      .map(_ => CreateUserApiKeyResponse(Right(Ok())))
      .recover {
        case _: DuplicateValueException =>
          CreateUserApiKeyResponse(Left(UserApiKeyExistsError()))
        case cause =>
          context.log.error("Unexpected error deleting user api key", cause)
          CreateUserApiKeyResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onDeleteKey(message: DeleteUserApiKeyRequest): Unit = {
    val DeleteUserApiKeyRequest(username, key, replyTo) = message
    userApiKeyStore
      .deleteKey(key, username)
      .map(_ => DeleteUserApiKeyResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          DeleteUserApiKeyResponse(Left(KeyNotFoundError()))
        case cause =>
          context.log.error("Unexpected error deleting user api key", cause)
          DeleteUserApiKeyResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onUpdateKey(message: UpdateUserApiKeyRequest): Unit = {
    val UpdateUserApiKeyRequest(username, key, name, enabled, replyTo) = message
    userApiKeyStore
      .updateKeyKey(key, username, name, enabled)
      .map(_ => UpdateUserApiKeyResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          UpdateUserApiKeyResponse(Left(KeyNotFoundError()))
        case cause =>
          context.log.error("Unexpected error updating user api key", cause)
          UpdateUserApiKeyResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }
}


object UserApiKeyStoreActor {

  val Key: ServiceKey[Message] = ServiceKey[Message]("UserApiKeyStore")

  def apply(userApiKeyStore: UserApiKeyStore): Behavior[Message] =
    Behaviors.setup(context => new UserApiKeyStoreActor(context, userApiKeyStore))


  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  trait Message extends CborSerializable

  //
  // GetApiKeysForUser
  //
  final case class GetApiKeysForUserRequest(username: String, replyTo: ActorRef[GetApiKeysForUserResponse]) extends Message


  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  trait GetApiKeysForUserError

  final case class GetApiKeysForUserResponse(keys: Either[GetApiKeysForUserError, Set[UserApiKey]]) extends CborSerializable

  //
  // GetUserApiKey
  //
  final case class GetUserApiKeyRequest(username: String, key: String, replyTo: ActorRef[GetUserApiKeyResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[KeyNotFoundError], name = "key_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  trait GetUserApiKeyError

  final case class GetUserApiKeyResponse(key: Either[GetUserApiKeyError, UserApiKey]) extends CborSerializable

  //
  // CreateUserApiKey
  //
  final case class CreateUserApiKeyRequest(username: String,
                                     name: String,
                                     enabled: Option[Boolean],
                                     replyTo: ActorRef[CreateUserApiKeyResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserApiKeyExistsError], name = "key_exists"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  trait CreateUserApiKeyError

  final case class UserApiKeyExistsError() extends CreateUserApiKeyError

  final case class CreateUserApiKeyResponse(response: Either[CreateUserApiKeyError, Ok]) extends CborSerializable

  //
  // DeleteUserApiKey
  //
  final case class DeleteUserApiKeyRequest(username: String, key: String, replyTo: ActorRef[DeleteUserApiKeyResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[KeyNotFoundError], name = "key_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  trait DeleteUserApiKeyError

  final case class DeleteUserApiKeyResponse(response: Either[DeleteUserApiKeyError, Ok]) extends CborSerializable

  //
  // UpdateUserApiKey
  //
  final case class UpdateUserApiKeyRequest(username: String, key: String, name: String, enabled: Boolean, replyTo: ActorRef[UpdateUserApiKeyResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[KeyNotFoundError], name = "key_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  trait UpdateUserApiKeyError

  final case class UpdateUserApiKeyResponse(response: Either[UpdateUserApiKeyError, Ok]) extends CborSerializable

  //
  // Commons Errors
  //
  final case class UserNotFoundError() extends AnyRef
    with GetApiKeysForUserError

  final case class KeyNotFoundError() extends AnyRef
    with GetUserApiKeyError
    with DeleteUserApiKeyError
    with UpdateUserApiKeyError

  final case class UnknownError() extends AnyRef
    with GetApiKeysForUserError
    with GetUserApiKeyError
    with CreateUserApiKeyError
    with DeleteUserApiKeyError
    with UpdateUserApiKeyError

}
