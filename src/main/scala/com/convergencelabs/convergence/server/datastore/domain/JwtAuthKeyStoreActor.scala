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
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.domain.JwtAuthKeyStore.KeyInfo
import com.convergencelabs.convergence.server.domain.JwtAuthKey
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import grizzled.slf4j.Logging

import scala.util.{Failure, Success}


class JwtAuthKeyStoreActor private[datastore](private[this] val context: ActorContext[JwtAuthKeyStoreActor.Message],
                                              private[this] val keyStore: JwtAuthKeyStore)
  extends AbstractBehavior[JwtAuthKeyStoreActor.Message](context) with Logging {

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
    keyStore.getKeys(offset, limit) match {
      case Success(keys) =>
        replyTo ! GetJwtAuthKeysSuccess(keys)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetKey(msg: GetJwtAuthKeyRequest): Unit = {
    val GetJwtAuthKeyRequest(id, replyTo) = msg
    keyStore.getKey(id) match {
      case Success(key) =>
        replyTo ! GetJwtAuthKeySuccess(key)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onDeleteKey(msg: DeleteJwtAuthKeyRequest): Unit = {
    val DeleteJwtAuthKeyRequest(id, replyTo) = msg
    keyStore.deleteKey(id) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onCreateKey(msg: CreateJwtAuthKeyRequest): Unit = {
    val CreateJwtAuthKeyRequest(key, replyTo) = msg
    keyStore.createKey(key) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onUpdateKey(msg: UpdateJwtAuthKeyRequest): Unit = {
    val UpdateJwtAuthKeyRequest(key, replyTo) = msg
    keyStore.updateKey(key) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }
}


object JwtAuthKeyStoreActor {
  def apply(keyStore: JwtAuthKeyStore): Behavior[Message] = Behaviors.setup { context =>
    new JwtAuthKeyStoreActor(context, keyStore)
  }

  sealed trait Message extends CborSerializable with DomainRestMessageBody

  case class GetJwtAuthKeysRequest(offset: Option[Int], limit: Option[Int], replyTo: ActorRef[GetJwtAuthKeysResponse]) extends Message

  sealed trait GetJwtAuthKeysResponse extends CborSerializable

  case class GetJwtAuthKeysSuccess(keys: List[JwtAuthKey]) extends GetJwtAuthKeysResponse


  case class GetJwtAuthKeyRequest(id: String, replyTo: ActorRef[GetJwtAuthKeyResponse]) extends Message

  sealed trait GetJwtAuthKeyResponse extends CborSerializable

  case class GetJwtAuthKeySuccess(key: Option[JwtAuthKey]) extends GetJwtAuthKeyResponse

  case class DeleteJwtAuthKeyRequest(id: String, replyTo: ActorRef[DeleteJwtAuthKeyResponse]) extends Message

  sealed trait DeleteJwtAuthKeyResponse extends CborSerializable

  case class UpdateJwtAuthKeyRequest(key: KeyInfo, replyTo: ActorRef[UpdateJwtAuthKeyResponse]) extends Message

  sealed trait UpdateJwtAuthKeyResponse extends CborSerializable

  case class CreateJwtAuthKeyRequest(key: KeyInfo, replyTo: ActorRef[CreateJwtAuthKeyResponse]) extends Message

  sealed trait CreateJwtAuthKeyResponse extends CborSerializable

  case class RequestFailure(cause: Throwable) extends CborSerializable
    with GetJwtAuthKeysResponse
    with GetJwtAuthKeyResponse
    with CreateJwtAuthKeyResponse
    with UpdateJwtAuthKeyResponse
    with DeleteJwtAuthKeyResponse

  case class RequestSuccess() extends CborSerializable
    with CreateJwtAuthKeyResponse
    with UpdateJwtAuthKeyResponse
    with DeleteJwtAuthKeyResponse
}
