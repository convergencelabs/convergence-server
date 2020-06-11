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
import com.convergencelabs.convergence.server.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import grizzled.slf4j.Logging

class ModelPermissionsStoreActor private(context: ActorContext[ModelPermissionsStoreActor.Message],
                                                    modelPermissionsStore: ModelPermissionsStore)
  extends AbstractBehavior[ModelPermissionsStoreActor.Message](context) with Logging {

  import ModelPermissionsStoreActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case msg: GetModelOverridesPermissionsRequest =>
        onGetModelOverridesCollectionPermissions(msg)
      case msg: SetModelOverridesPermissionsRequest =>
        onSetModelOverridesCollectionPermissions(msg)
      case msg: GetModelPermissionsRequest =>
        onGetModelPermissions(msg)
      case msg: GetModelWorldPermissionsRequest =>
        onGetModelWorldPermissions(msg)
      case msg: SetModelWorldPermissionsRequest =>
        onSetModelWorldPermissions(msg)
      case msg: GetAllModelUserPermissionsRequest =>
        onGetAllModelUserPermissions(msg)
      case msg: GetModelUserPermissionsRequest =>
        onGetModelUserPermissions(msg)
      case msg: SetModelUserPermissionsRequest =>
        onSetModelUserPermissions(msg)
      case msg: RemoveModelUserPermissionsRequest =>
        onRemoveModelUserPermissions(msg)
    }

    Behaviors.same
  }


  private[this] def onGetModelPermissions(msg: GetModelPermissionsRequest): Unit = {
    val GetModelPermissionsRequest(modelId, replyTo) = msg
    (for {
      overrideWorld <- modelPermissionsStore.modelOverridesCollectionPermissions(modelId)
      worldPermissions <- modelPermissionsStore.getModelWorldPermissions(modelId)
      userPermissions <- modelPermissionsStore.getAllModelUserPermissions(modelId)
    } yield {
      val userPermissionsList = userPermissions.toList.map {
        case Tuple2(userId, permissions) => ModelUserPermissions(userId, permissions)
      }
      ModelPermissionsData(overrideWorld, worldPermissions, userPermissionsList)
    })
      .map(Right(_))
      .recover {
        case _: EntityNotFoundException =>
          Left(ModelNotFoundError())
        case cause =>
          context.log.error("Unexpected error getting model permissions", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetModelPermissionsResponse(_))
  }

  private[this] def onGetModelOverridesCollectionPermissions(msg: GetModelOverridesPermissionsRequest): Unit = {
    val GetModelOverridesPermissionsRequest(modelId, replyTo) = msg
    modelPermissionsStore
      .modelOverridesCollectionPermissions(modelId)
      .map(Right(_))
      .recover {
        case _: EntityNotFoundException =>
          Left(ModelNotFoundError())
        case cause =>
          context.log.error("Unexpected error getting model overrides permissions", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetModelOverridesPermissionsResponse(_))
  }

  private[this] def onSetModelOverridesCollectionPermissions(msg: SetModelOverridesPermissionsRequest): Unit = {
    val SetModelOverridesPermissionsRequest(modelId, overridePermissions, replyTo) = msg
    modelPermissionsStore
      .setOverrideCollectionPermissions(modelId, overridePermissions)
      .map(_ => Right(()))
      .recover {
        case _: EntityNotFoundException =>
          Left(ModelNotFoundError())
        case cause =>
          context.log.error("Unexpected error setting model override permissions", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! SetModelOverridesPermissionsResponse(_))
  }

  private[this] def onGetModelWorldPermissions(msg: GetModelWorldPermissionsRequest): Unit = {
    val GetModelWorldPermissionsRequest(modelId, replyTo) = msg
    modelPermissionsStore
      .getModelWorldPermissions(modelId)
      .map(Right(_))
      .recover {
        case _: EntityNotFoundException =>
          Left(ModelNotFoundError())
        case cause =>
          context.log.error("Unexpected error getting model world permissions", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetModelWorldPermissionsResponse(_))
  }

  private[this] def onSetModelWorldPermissions(msg: SetModelWorldPermissionsRequest): Unit = {
    val SetModelWorldPermissionsRequest(modelId, permissions, replyTo) = msg
    modelPermissionsStore
      .setModelWorldPermissions(modelId, permissions)
      .map(_ => Right(()))
      .recover {
        case _: EntityNotFoundException =>
          Left(ModelNotFoundError())
        case cause =>
          context.log.error("Unexpected error setting model world permissions", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! SetModelWorldPermissionsResponse(_))
  }

  private[this] def onGetAllModelUserPermissions(msg: GetAllModelUserPermissionsRequest): Unit = {
    val GetAllModelUserPermissionsRequest(modelId, replyTo) = msg
    modelPermissionsStore.getAllModelUserPermissions(modelId).map(_.toList.map {
      case Tuple2(username, permissions) => ModelUserPermissions(username, permissions)
    })
      .map(Right(_))
      .recover {
        case _: EntityNotFoundException =>
          Left(ModelNotFoundError())
        case cause =>
          context.log.error("Unexpected error getting all model user permissions", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetAllModelUserPermissionsResponse(_))
  }

  private[this] def onGetModelUserPermissions(msg: GetModelUserPermissionsRequest): Unit = {
    val GetModelUserPermissionsRequest(modelId, userId, replyTo) = msg
    modelPermissionsStore
      .getModelUserPermissions(modelId, userId)
      .map(_.map(Right(_)).getOrElse(Left(ModelNotFoundError())))
      .recover {
        case cause =>
          context.log.error("Unexpected error getting model user permissions", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetModelUserPermissionsResponse(_))
  }

  private[this] def onSetModelUserPermissions(msg: SetModelUserPermissionsRequest): Unit = {
    val SetModelUserPermissionsRequest(modelId, userId, permissions: ModelPermissions, replyTo) = msg
    modelPermissionsStore
      .updateModelUserPermissions(modelId, userId, permissions)
      .map(_ => Right(()))
      .recover {
        case _: EntityNotFoundException =>
          Left(ModelNotFoundError())
        case cause =>
          context.log.error("Unexpected error setting model user permissions", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! SetModelUserPermissionsResponse(_))
  }

  private[this] def onRemoveModelUserPermissions(msg: RemoveModelUserPermissionsRequest): Unit = {
    val RemoveModelUserPermissionsRequest(modelId, userId, replyTo) = msg
    modelPermissionsStore
      .removeModelUserPermissions(modelId, userId)
      .map(_ => Right(()))
      .recover {
        case _: EntityNotFoundException =>
          Left(ModelNotFoundError())
        case cause =>
          context.log.error("Unexpected error removing model user permissions", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! RemoveModelUserPermissionsResponse(_))
  }
}

object ModelPermissionsStoreActor {
  def apply(modelPermissionsStore: ModelPermissionsStore): Behavior[Message] =
    Behaviors.setup(context => new ModelPermissionsStoreActor(context, modelPermissionsStore))


  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable with DomainRestMessageBody

  //
  // GetModelOverridesPermissions
  //
  final case class GetModelOverridesPermissionsRequest(modelId: String, replyTo: ActorRef[GetModelOverridesPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetModelOverridesPermissionsError

  final case class GetModelOverridesPermissionsResponse(overrides: Either[GetModelOverridesPermissionsError, Boolean]) extends CborSerializable

  //
  // SetModelOverridesPermissions
  //

  final case class SetModelOverridesPermissionsRequest(modelId: String, overridesPermissions: Boolean, replyTo: ActorRef[SetModelOverridesPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetModelOverridesPermissionsError

  final case class SetModelOverridesPermissionsResponse(response: Either[SetModelOverridesPermissionsError, Unit]) extends CborSerializable

  //
  // GetModelPermissions
  //
  final case class GetModelPermissionsRequest(modelId: String, replyTo: ActorRef[GetModelPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetModelPermissionsError

  final case class GetModelPermissionsResponse(permissions: Either[GetModelPermissionsError, ModelPermissionsData]) extends CborSerializable

  //
  // GetModelWorldPermissions
  //
  final case class GetModelWorldPermissionsRequest(modelId: String, replyTo: ActorRef[GetModelWorldPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetModelWorldPermissionsError

  final case class GetModelWorldPermissionsResponse(permissions: Either[GetModelWorldPermissionsError, ModelPermissions]) extends CborSerializable

  //
  // SetModelWorldPermissionsRequest
  //
  final case class SetModelWorldPermissionsRequest(modelId: String, permissions: ModelPermissions, replyTo: ActorRef[SetModelWorldPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetModelWorldPermissionsError

  final case class SetModelWorldPermissionsResponse(response: Either[SetModelWorldPermissionsError, Unit]) extends CborSerializable

  //
  // GetAllModelUserPermissions
  //
  final case class GetAllModelUserPermissionsRequest(modelId: String, replyTo: ActorRef[GetAllModelUserPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetAllModelUserPermissionsError

  final case class GetAllModelUserPermissionsResponse(permissions: Either[GetAllModelUserPermissionsError, List[ModelUserPermissions]]) extends CborSerializable


  //
  // GetModelUserPermissions
  //
  case class GetModelUserPermissionsRequest(modelId: String, userId: DomainUserId, replyTo: ActorRef[GetModelUserPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetModelUserPermissionsError

  final case class GetModelUserPermissionsResponse(permissions: Either[GetModelUserPermissionsError, ModelPermissions]) extends CborSerializable

  //
  // SetModelUserPermissions
  //
  final case class SetModelUserPermissionsRequest(modelId: String, userId: DomainUserId, permissions: ModelPermissions, replyTo: ActorRef[SetModelUserPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetModelUserPermissionsError

  final case class SetModelUserPermissionsResponse(response: Either[SetModelUserPermissionsError, Unit]) extends CborSerializable

  //
  // SetModelUserPermissions
  //
  final case class RemoveModelUserPermissionsRequest(modelId: String, userId: DomainUserId, replyTo: ActorRef[RemoveModelUserPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait RemoveModelUserPermissionsError

  final case class RemoveModelUserPermissionsResponse(response: Either[RemoveModelUserPermissionsError, Unit]) extends CborSerializable

  //
  // Common Errors
  //

  final case class ModelNotFoundError() extends AnyRef
    with GetModelOverridesPermissionsError
    with GetModelPermissionsError
    with GetModelWorldPermissionsError
    with GetAllModelUserPermissionsError
    with GetModelUserPermissionsError
    with SetModelOverridesPermissionsError
    with SetModelWorldPermissionsError
    with SetModelUserPermissionsError
    with RemoveModelUserPermissionsError

  final case class UnknownError() extends AnyRef
    with GetModelOverridesPermissionsError
    with GetModelPermissionsError
    with GetModelWorldPermissionsError
    with GetAllModelUserPermissionsError
    with GetModelUserPermissionsError
    with SetModelOverridesPermissionsError
    with SetModelWorldPermissionsError
    with SetModelUserPermissionsError
    with RemoveModelUserPermissionsError


  //
  // Data Classes
  //
  final case class ModelUserPermissions(userId: DomainUserId, permissions: ModelPermissions)

  final case class ModelPermissionsData(overrideWorld: Boolean, worldPermissions: ModelPermissions, userPermissions: List[ModelUserPermissions])

}
