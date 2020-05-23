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
import com.convergencelabs.convergence.server.domain.DomainUserId

class ModelPermissionsStoreActor private[datastore](private[this] val modelPermissionsStore: ModelPermissionsStore)
  extends StoreActor with ActorLogging {

  import ModelPermissionsStoreActor._

  def receive: Receive = {
    case GetModelOverridesPermissionsRequest(modelId) =>
      onGetModelOverridesCollectionPermissions(modelId)
    case SetModelOverridesPermissionsRequest(modelId, overridesPermissions) =>
      onSetModelOverridesCollectionPermissions(modelId, overridesPermissions)
    case GetModelPermissionsRequest(modelId) =>
      onGetModelPermissions(modelId)
    case GetModelWorldPermissionsRequest(modelId) =>
      onGetModelWorldPermissions(modelId)
    case SetModelWorldPermissionsRequest(modelId, permissions) =>
      onSetModelWorldPermissions(modelId, permissions)
    case GetAllModelUserPermissionsRequest(modelId) =>
      onGetAllModelUserPermissions(modelId)
    case GetModelUserPermissionsRequest(modelId, userId) =>
      onGetModelUserPermissions(modelId, userId)
    case SetModelUserPermissionsRequest(modelId, userId, permissions: ModelPermissions) =>
      onSetModelUserPermissions(modelId, userId, permissions)
    case RemoveModelUserPermissionsRequest(modelId, userId) =>
      onRemoveModelUserPermissions(modelId, userId)
    case message: Any =>
      unhandled(message)
  }

  private[this] def onGetModelPermissions(modelId: String): Unit = {
    val result = for {
      overrideWorld <- modelPermissionsStore.modelOverridesCollectionPermissions(modelId)
      worldPermissions <- modelPermissionsStore.getModelWorldPermissions(modelId)
      userPermissions <- modelPermissionsStore.getAllModelUserPermissions(modelId)
    } yield {
      val userPermissionsList = userPermissions.toList.map {
        case Tuple2(userId, permissions) => ModelUserPermissions(userId, permissions)
      }
      GetModelPermissionsResponse(ModelPermissionsResponse(overrideWorld, worldPermissions, userPermissionsList))
    }
    reply(result)
  }

  private[this] def onGetModelOverridesCollectionPermissions(modelId: String): Unit = {
    reply(modelPermissionsStore.modelOverridesCollectionPermissions(modelId).map(GetModelOverridesPermissionsResponse))
  }

  private[this] def onSetModelOverridesCollectionPermissions(modelId: String, overridePermissions: Boolean): Unit = {
    reply(modelPermissionsStore.setOverrideCollectionPermissions(modelId, overridePermissions))
  }

  private[this] def onGetModelWorldPermissions(modelId: String): Unit = {
    reply(modelPermissionsStore.getModelWorldPermissions(modelId).map(GetModelWorldPermissionsResponse))
  }

  private[this] def onSetModelWorldPermissions(modelId: String, permissions: ModelPermissions): Unit = {
    reply(modelPermissionsStore.setModelWorldPermissions(modelId, permissions))
  }

  private[this] def onGetAllModelUserPermissions(modelId: String): Unit = {
    reply(modelPermissionsStore.getAllModelUserPermissions(modelId).map(_.toList.map {
      case Tuple2(username, permissions) => ModelUserPermissions(username, permissions)
    }).map(GetAllModelUserPermissionsResponse))
  }

  private[this] def onGetModelUserPermissions(modelId: String, userId: DomainUserId): Unit = {
    reply(modelPermissionsStore.getModelUserPermissions(modelId, userId).map(GetModelUserPermissionsResponse))
  }

  private[this] def onSetModelUserPermissions(modelId: String, userId: DomainUserId, permissions: ModelPermissions): Unit = {
    reply(modelPermissionsStore.updateModelUserPermissions(modelId, userId, permissions))
  }

  private[this] def onRemoveModelUserPermissions(modelId: String, userId: DomainUserId): Unit = {
    reply(modelPermissionsStore.removeModelUserPermissions(modelId, userId))
  }
}

object ModelPermissionsStoreActor {
  def props(modelPermissionsStore: ModelPermissionsStore): Props =
    Props(new ModelPermissionsStoreActor(modelPermissionsStore))

  sealed trait ModelPermissionsStoreRequest extends CborSerializable

  case class GetModelOverridesPermissionsRequest(modelId: String) extends ModelPermissionsStoreRequest

  case class GetModelOverridesPermissionsResponse(overrides: Boolean) extends CborSerializable

  case class SetModelOverridesPermissionsRequest(modelId: String, overridesPermissions: Boolean) extends ModelPermissionsStoreRequest

  case class GetModelPermissionsRequest(modelId: String) extends ModelPermissionsStoreRequest

  case class GetModelPermissionsResponse(permissions: ModelPermissionsResponse) extends CborSerializable

  case class GetModelWorldPermissionsRequest(modelId: String) extends ModelPermissionsStoreRequest

  case class GetModelWorldPermissionsResponse(permissions: ModelPermissions) extends CborSerializable

  case class SetModelWorldPermissionsRequest(modelId: String, permissions: ModelPermissions) extends ModelPermissionsStoreRequest

  case class GetAllModelUserPermissionsRequest(modelId: String) extends ModelPermissionsStoreRequest

  case class GetAllModelUserPermissionsResponse(permissions: List[ModelUserPermissions]) extends CborSerializable

  case class GetModelUserPermissionsRequest(modelId: String, userId: DomainUserId) extends ModelPermissionsStoreRequest

  case class GetModelUserPermissionsResponse(permissions: Option[ModelPermissions]) extends CborSerializable

  case class SetModelUserPermissionsRequest(modelId: String, userId: DomainUserId, permissions: ModelPermissions) extends ModelPermissionsStoreRequest

  case class RemoveModelUserPermissionsRequest(modelId: String, userId: DomainUserId) extends ModelPermissionsStoreRequest

  case class ModelUserPermissions(userId: DomainUserId, permissions: ModelPermissions)

  case class ModelPermissionsResponse(overrideWorld: Boolean, worldPermissions: ModelPermissions, userPermissions: List[ModelUserPermissions])
}
