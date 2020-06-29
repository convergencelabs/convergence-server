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

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.domain.JwtAuthKeyStore.KeyInfo
import com.convergencelabs.convergence.server.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.domain.JwtAuthKey
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

class JwtAuthKeyStoreActor private(context: ActorContext[JwtAuthKeyStoreActor.Message],
                                   keyStore: JwtAuthKeyStore)
  extends AbstractBehavior[JwtAuthKeyStoreActor.Message](context) {

  import JwtAuthKeyStoreActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case msg: GetJwtAuthKeysRequest =>
        onGetKeys(msg)
      case msg: GetJwtAuthKeyRequest =>
        onGetKey(msg)
      case msg: DeleteJwtAuthKeyRequest =>
        onDeleteKey(msg)
      case msg: CreateJwtAuthKeyRequest =>
        onCreateKey(msg)
      case msg: UpdateJwtAuthKeyRequest =>
        onUpdateKey(msg)
    }
    Behaviors.same
  }

  private[this] def onGetKeys(msg: GetJwtAuthKeysRequest): Unit = {
    val GetJwtAuthKeysRequest(offset, limit, replyTo) = msg
    keyStore
      .getKeys(offset, limit)
      .map(keys => GetJwtAuthKeysResponse(Right(keys)))
      .recover { cause =>
        context.log.error("Unexpected error getting jwt auth keys", cause)
        GetJwtAuthKeysResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetKey(msg: GetJwtAuthKeyRequest): Unit = {
    val GetJwtAuthKeyRequest(id, replyTo) = msg
    keyStore
      .getKey(id)
      .map(_.map(key => GetJwtAuthKeyResponse(Right(key))).getOrElse(GetJwtAuthKeyResponse(Left(JwtAuthKeyNotFoundError()))))
      .recover { cause =>
        context.log.error("Unexpected error getting jwt auth key", cause)
        GetJwtAuthKeyResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onDeleteKey(msg: DeleteJwtAuthKeyRequest): Unit = {
    val DeleteJwtAuthKeyRequest(id, replyTo) = msg
    keyStore
      .deleteKey(id)
      .map(_ => DeleteJwtAuthKeyResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          DeleteJwtAuthKeyResponse(Left(JwtAuthKeyNotFoundError()))
        case cause =>
          context.log.error("Unexpected error deleting jwt auth key", cause)
          DeleteJwtAuthKeyResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onCreateKey(msg: CreateJwtAuthKeyRequest): Unit = {
    val CreateJwtAuthKeyRequest(key, replyTo) = msg
    keyStore
      .createKey(key)
      .map(_ => CreateJwtAuthKeyResponse(Right(Ok())))
      .recover {
        case _: DuplicateValueException =>
          CreateJwtAuthKeyResponse(Left(JwtAuthKeyExistsError()))
        case cause =>
          context.log.error("Unexpected error creating jwt auth key", cause)
          CreateJwtAuthKeyResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onUpdateKey(msg: UpdateJwtAuthKeyRequest): Unit = {
    val UpdateJwtAuthKeyRequest(key, replyTo) = msg
    keyStore
      .updateKey(key)
      .map(_ => UpdateJwtAuthKeyResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          UpdateJwtAuthKeyResponse(Left(JwtAuthKeyNotFoundError()))
        case cause =>
          context.log.error("Unexpected error updating jwt auth key", cause)
          UpdateJwtAuthKeyResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }
}


object JwtAuthKeyStoreActor {
  def apply(keyStore: JwtAuthKeyStore): Behavior[Message] =
    Behaviors.setup(context => new JwtAuthKeyStoreActor(context, keyStore))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DeleteJwtAuthKeyRequest], name = "delete_key"),
    new JsonSubTypes.Type(value = classOf[CreateJwtAuthKeyRequest], name = "create_key"),
    new JsonSubTypes.Type(value = classOf[GetJwtAuthKeyRequest], name = "get_key"),
    new JsonSubTypes.Type(value = classOf[GetJwtAuthKeysRequest], name = "get_keys"),
    new JsonSubTypes.Type(value = classOf[UpdateJwtAuthKeyRequest], name = "update_key")
  ))
  sealed trait Message extends CborSerializable

  //
  // GetJwtAuthKeys
  //
  final case class GetJwtAuthKeysRequest(@JsonDeserialize(contentAs = classOf[Long])
                                         offset: QueryOffset,
                                         @JsonDeserialize(contentAs = classOf[Long])
                                         limit: QueryLimit,
                                         replyTo: ActorRef[GetJwtAuthKeysResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetJwtAuthKeysError

  final case class GetJwtAuthKeysResponse(keys: Either[GetJwtAuthKeysError, List[JwtAuthKey]]) extends CborSerializable

  //
  // GetJwtAuthKey
  //
  final case class GetJwtAuthKeyRequest(id: String, replyTo: ActorRef[GetJwtAuthKeyResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[JwtAuthKeyNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetJwtAuthKeyError

  final case class GetJwtAuthKeyResponse(key: Either[GetJwtAuthKeyError, JwtAuthKey]) extends CborSerializable

  //
  // DeleteJwtAuthKey
  //
  final case class DeleteJwtAuthKeyRequest(id: String, replyTo: ActorRef[DeleteJwtAuthKeyResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[JwtAuthKeyNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait DeleteJwtAuthKeyError

  final case class DeleteJwtAuthKeyResponse(response: Either[DeleteJwtAuthKeyError, Ok]) extends CborSerializable

  //
  // UpdateJwtAuthKey
  //
  final case class UpdateJwtAuthKeyRequest(key: KeyInfo, replyTo: ActorRef[UpdateJwtAuthKeyResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[JwtAuthKeyNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait UpdateJwtAuthKeyError

  final case class UpdateJwtAuthKeyResponse(response: Either[UpdateJwtAuthKeyError, Ok]) extends CborSerializable


  //
  // CreateJwtAuthKey
  //
  final case class CreateJwtAuthKeyRequest(key: KeyInfo, replyTo: ActorRef[CreateJwtAuthKeyResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[JwtAuthKeyExistsError], name = "key_exists"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait CreateJwtAuthKeyError

  final case class JwtAuthKeyExistsError() extends CreateJwtAuthKeyError

  final case class CreateJwtAuthKeyResponse(response: Either[CreateJwtAuthKeyError, Ok]) extends CborSerializable

  //
  // Common Errors
  //

  final case class JwtAuthKeyNotFoundError() extends AnyRef
    with GetJwtAuthKeyError
    with UpdateJwtAuthKeyError
    with DeleteJwtAuthKeyError

  final case class UnknownError() extends AnyRef
    with GetJwtAuthKeysError
    with GetJwtAuthKeyError
    with CreateJwtAuthKeyError
    with UpdateJwtAuthKeyError
    with DeleteJwtAuthKeyError

}
