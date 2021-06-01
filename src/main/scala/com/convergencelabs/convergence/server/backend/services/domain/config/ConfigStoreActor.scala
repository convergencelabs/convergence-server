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

package com.convergencelabs.convergence.server.backend.services.domain.config

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.backend.datastore.domain.config.DomainConfigStore
import com.convergencelabs.convergence.server.model.domain.{CollectionConfig, ModelSnapshotConfig}
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

private final class ConfigStoreActor(context: ActorContext[ConfigStoreActor.Message],
                                     store: DomainConfigStore)
  extends AbstractBehavior[ConfigStoreActor.Message](context) {

  import ConfigStoreActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case msg: GetAnonymousAuthRequest =>
        onGetAnonymousAuthEnabled(msg)
      case msg: SetAnonymousAuthRequest =>
        onSetAnonymousAuthEnabled(msg)
      case msg: GetModelSnapshotPolicyRequest =>
        onGetModelSnapshotPolicy(msg)
      case msg: SetModelSnapshotPolicyRequest =>
        onSetModelSnapshotPolicy(msg)
      case msg: GetCollectionConfigRequest =>
        onGetCollectionConfig(msg)
      case msg: SetCollectionConfigRequest =>
        onSetCollectionConfig(msg)
    }

    Behaviors.same
  }


  private[this] def onGetAnonymousAuthEnabled(msg: GetAnonymousAuthRequest): Unit = {
    val GetAnonymousAuthRequest(replyTo) = msg
    store
      .isAnonymousAuthEnabled()
      .map(enabled => GetAnonymousAuthResponse(Right(enabled)))
      .recover { cause =>
        context.log.error("Unexpected error getting anonymous authentication", cause)
        GetAnonymousAuthResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onSetAnonymousAuthEnabled(msg: SetAnonymousAuthRequest): Unit = {
    val SetAnonymousAuthRequest(enabled, replyTo) = msg
    store
      .setAnonymousAuthEnabled(enabled)
      .map(_ => SetAnonymousAuthResponse(Right(Ok())))
      .recover { cause =>
        context.log.error("Unexpected error setting anonymous authentication", cause)
        SetAnonymousAuthResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetModelSnapshotPolicy(msg: GetModelSnapshotPolicyRequest): Unit = {
    val GetModelSnapshotPolicyRequest(replyTo) = msg
    store
      .getModelSnapshotConfig()
      .map(config => GetModelSnapshotPolicyResponse(Right(config)))
      .recover { cause =>
        context.log.error("Unexpected error getting model snapshot policy", cause)
        GetModelSnapshotPolicyResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onSetModelSnapshotPolicy(msg: SetModelSnapshotPolicyRequest): Unit = {
    val SetModelSnapshotPolicyRequest(policy, replyTo) = msg
    store
      .setModelSnapshotConfig(policy)
      .map(_ => SetModelSnapshotPolicyResponse(Right(Ok())))
      .recover { cause =>
        context.log.error("Unexpected error setting model snapshot policy", cause)
        SetModelSnapshotPolicyResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetCollectionConfig(msg: GetCollectionConfigRequest): Unit = {
    val GetCollectionConfigRequest(replyTo) = msg
    store
      .getCollectionConfig()
      .map(config => Right(config))
      .recover { cause =>
        context.log.error("Unexpected error getting collection config", cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! GetCollectionConfigResponse(_))
  }

  private[this] def onSetCollectionConfig(msg: SetCollectionConfigRequest): Unit = {
    val SetCollectionConfigRequest(config, replyTo) = msg
    store
      .setCollectionConfig(config)
      .map(_ => Right(Ok()))
      .recover { cause =>
        context.log.error("Unexpected error setting collection config", cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! SetCollectionConfigResponse(_))
  }
}


object ConfigStoreActor {
  def apply(store: DomainConfigStore): Behavior[Message] =
    Behaviors.setup(context => new ConfigStoreActor(context, store))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GetAnonymousAuthRequest], name = "get_anonymous_auth"),
    new JsonSubTypes.Type(value = classOf[GetModelSnapshotPolicyRequest], name = "get_model_snapshot_config"),
    new JsonSubTypes.Type(value = classOf[GetCollectionConfigRequest], name = "get_collection_config"),
    new JsonSubTypes.Type(value = classOf[SetAnonymousAuthRequest], name = "set_anonymous_auth"),
    new JsonSubTypes.Type(value = classOf[SetModelSnapshotPolicyRequest], name = "set_model_snapshot_config"),
    new JsonSubTypes.Type(value = classOf[SetCollectionConfigRequest], name = "set_collection_config"),
  ))
  sealed trait Message extends CborSerializable

  //
  // GetAnonymousAuth
  //
  final case class GetAnonymousAuthRequest(replyTo: ActorRef[GetAnonymousAuthResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetAnonymousAuthError

  final case class GetAnonymousAuthResponse(enabled: Either[GetAnonymousAuthError, Boolean]) extends CborSerializable


  //
  // SetAnonymousAuth
  //
  final case class SetAnonymousAuthRequest(enabled: Boolean, replyTo: ActorRef[SetAnonymousAuthResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetAnonymousAuthError

  final case class SetAnonymousAuthResponse(response: Either[SetAnonymousAuthError, Ok]) extends CborSerializable


  //
  // GetModelSnapshotPolicy
  //
  final case class GetModelSnapshotPolicyRequest(replyTo: ActorRef[GetModelSnapshotPolicyResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetModelSnapshotPolicyError

  final case class GetModelSnapshotPolicyResponse(policy: Either[GetModelSnapshotPolicyError, ModelSnapshotConfig]) extends CborSerializable

  //
  // SetModelSnapshotPolicy
  //
  final case class SetModelSnapshotPolicyRequest(policy: ModelSnapshotConfig, replyTo: ActorRef[SetModelSnapshotPolicyResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetModelSnapshotPolicyError

  final case class SetModelSnapshotPolicyResponse(response: Either[SetModelSnapshotPolicyError, Ok]) extends CborSerializable

  //
  // GetCollectionConfig
  //
  final case class GetCollectionConfigRequest(replyTo: ActorRef[GetCollectionConfigResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetCollectionConfigError

  final case class GetCollectionConfigResponse(response: Either[GetCollectionConfigError, CollectionConfig]) extends CborSerializable


  //
  // SetCollectionConfig
  //
  final case class SetCollectionConfigRequest(config: CollectionConfig, replyTo: ActorRef[SetCollectionConfigResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetCollectionConfigError

  final case class SetCollectionConfigResponse(response: Either[SetCollectionConfigError, Ok]) extends CborSerializable


  //
  // Common Errors
  //
  final case class UnknownError() extends AnyRef
    with GetAnonymousAuthError
    with SetAnonymousAuthError
    with GetModelSnapshotPolicyError
    with SetModelSnapshotPolicyError
    with GetCollectionConfigError
    with SetCollectionConfigError
}