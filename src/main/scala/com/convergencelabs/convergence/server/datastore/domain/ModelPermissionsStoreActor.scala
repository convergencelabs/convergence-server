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
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import grizzled.slf4j.Logging

import scala.util.{Failure, Success}

class ModelPermissionsStoreActor private[datastore](private[this] val context: ActorContext[ModelPermissionsStoreActor.Message],
                                                    private[this] val modelPermissionsStore: ModelPermissionsStore)
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
      GetModelPermissionsSuccess(ModelPermissionsData(overrideWorld, worldPermissions, userPermissionsList))
    }) match {
      case Success(response) =>
        replyTo ! response
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }

  private[this] def onGetModelOverridesCollectionPermissions(msg: GetModelOverridesPermissionsRequest): Unit = {
    val GetModelOverridesPermissionsRequest(modelId, replyTo) = msg
    modelPermissionsStore.modelOverridesCollectionPermissions(modelId) match {
      case Success(overrides) =>
        replyTo ! GetModelOverridesPermissionsSuccess(overrides)
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }

  private[this] def onSetModelOverridesCollectionPermissions(msg: SetModelOverridesPermissionsRequest): Unit = {
    val SetModelOverridesPermissionsRequest(modelId, overridePermissions, replyTo) = msg
    modelPermissionsStore.setOverrideCollectionPermissions(modelId, overridePermissions) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }

  private[this] def onGetModelWorldPermissions(msg: GetModelWorldPermissionsRequest): Unit = {
    val GetModelWorldPermissionsRequest(modelId, replyTo) = msg
    modelPermissionsStore.getModelWorldPermissions(modelId) match {
      case Success(permissions) =>
        replyTo ! GetModelWorldPermissionsSuccess(permissions)
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }

  private[this] def onSetModelWorldPermissions(msg: SetModelWorldPermissionsRequest): Unit = {
    val SetModelWorldPermissionsRequest(modelId, permissions, replyTo) = msg
    modelPermissionsStore.setModelWorldPermissions(modelId, permissions) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }

  private[this] def onGetAllModelUserPermissions(msg: GetAllModelUserPermissionsRequest): Unit = {
    val GetAllModelUserPermissionsRequest(modelId, replyTo) = msg
    modelPermissionsStore.getAllModelUserPermissions(modelId).map(_.toList.map {
      case Tuple2(username, permissions) => ModelUserPermissions(username, permissions)
    }) match {
      case Success(permissions) =>
        replyTo ! GetAllModelUserPermissionsSuccess(permissions)
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }

  private[this] def onGetModelUserPermissions(msg: GetModelUserPermissionsRequest): Unit = {
    val GetModelUserPermissionsRequest(modelId, userId, replyTo) = msg
    modelPermissionsStore.getModelUserPermissions(modelId, userId) match {
      case Success(permissions) =>
        replyTo ! GetModelUserPermissionsSuccess(permissions)
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }

  private[this] def onSetModelUserPermissions(msg: SetModelUserPermissionsRequest): Unit = {
    val SetModelUserPermissionsRequest(modelId, userId, permissions: ModelPermissions, replyTo) = msg
    modelPermissionsStore.updateModelUserPermissions(modelId, userId, permissions) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }

  private[this] def onRemoveModelUserPermissions(msg: RemoveModelUserPermissionsRequest): Unit = {
    val RemoveModelUserPermissionsRequest(modelId, userId, replyTo) = msg
    modelPermissionsStore.removeModelUserPermissions(modelId, userId) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }
}

object ModelPermissionsStoreActor {
  def apply(modelPermissionsStore: ModelPermissionsStore): Behavior[Message] = Behaviors.setup { context =>
    new ModelPermissionsStoreActor(context, modelPermissionsStore)
  }

  sealed trait Message extends CborSerializable with DomainRestMessageBody

  //
  // GetModelOverridesPermissions
  //
  case class GetModelOverridesPermissionsRequest(modelId: String, replyTo: ActorRef[GetModelOverridesPermissionsResponse]) extends Message

  sealed trait GetModelOverridesPermissionsResponse extends CborSerializable

  case class GetModelOverridesPermissionsSuccess(overrides: Boolean) extends GetModelOverridesPermissionsResponse

  //
  // SetModelOverridesPermissions
  //

  case class SetModelOverridesPermissionsRequest(modelId: String, overridesPermissions: Boolean, replyTo: ActorRef[SetModelOverridesPermissionsResponse]) extends Message

  sealed trait SetModelOverridesPermissionsResponse extends CborSerializable

  //
  // GetModelPermissions
  //
  case class GetModelPermissionsRequest(modelId: String, replyTo: ActorRef[GetModelPermissionsResponse]) extends Message

  sealed trait GetModelPermissionsResponse extends CborSerializable

  case class GetModelPermissionsSuccess(permissions: ModelPermissionsData) extends GetModelPermissionsResponse

  //
  // GetModelWorldPermissions
  //
  case class GetModelWorldPermissionsRequest(modelId: String, replyTo: ActorRef[GetModelWorldPermissionsResponse]) extends Message

  sealed trait GetModelWorldPermissionsResponse extends CborSerializable

  case class GetModelWorldPermissionsSuccess(permissions: ModelPermissions) extends GetModelWorldPermissionsResponse

  //
  // SetModelWorldPermissionsRequest
  //
  case class SetModelWorldPermissionsRequest(modelId: String, permissions: ModelPermissions, replyTo: ActorRef[SetModelWorldPermissionsResponse]) extends Message

  sealed trait SetModelWorldPermissionsResponse extends CborSerializable

  //
  // GetAllModelUserPermissions
  //
  case class GetAllModelUserPermissionsRequest(modelId: String, replyTo: ActorRef[GetAllModelUserPermissionsResponse]) extends Message

  sealed trait GetAllModelUserPermissionsResponse extends CborSerializable

  case class GetAllModelUserPermissionsSuccess(permissions: List[ModelUserPermissions]) extends GetAllModelUserPermissionsResponse


  //
  // GetModelUserPermissions
  //
  case class GetModelUserPermissionsRequest(modelId: String, userId: DomainUserId, replyTo: ActorRef[GetModelUserPermissionsResponse]) extends Message

  sealed trait GetModelUserPermissionsResponse extends CborSerializable

  case class GetModelUserPermissionsSuccess(permissions: Option[ModelPermissions]) extends GetModelUserPermissionsResponse

  //
  // SetModelUserPermissions
  //
  case class SetModelUserPermissionsRequest(modelId: String, userId: DomainUserId, permissions: ModelPermissions, replyTo: ActorRef[SetModelUserPermissionsResponse]) extends Message

  sealed trait SetModelUserPermissionsResponse extends CborSerializable

  //
  // SetModelUserPermissions
  //
  case class RemoveModelUserPermissionsRequest(modelId: String, userId: DomainUserId, replyTo: ActorRef[RemoveModelUserPermissionsResponse]) extends Message

  sealed trait RemoveModelUserPermissionsResponse extends CborSerializable


  case class RequestFailure(cause: Throwable) extends CborSerializable
    with GetModelOverridesPermissionsResponse
    with GetModelPermissionsResponse
    with GetModelWorldPermissionsResponse
    with GetAllModelUserPermissionsResponse
    with GetModelUserPermissionsResponse
    with SetModelOverridesPermissionsResponse
    with SetModelWorldPermissionsResponse
    with SetModelUserPermissionsResponse
    with RemoveModelUserPermissionsResponse


  case class RequestSuccess() extends CborSerializable
    with SetModelOverridesPermissionsResponse
    with SetModelWorldPermissionsResponse
    with SetModelUserPermissionsResponse
    with RemoveModelUserPermissionsResponse


  //
  // Data Classes
  //
  case class ModelUserPermissions(userId: DomainUserId, permissions: ModelPermissions)

  case class ModelPermissionsData(overrideWorld: Boolean, worldPermissions: ModelPermissions, userPermissions: List[ModelUserPermissions])

}
