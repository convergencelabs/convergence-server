/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain


import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.domain.DomainUserId

object ModelPermissionsStoreActor {
  def props(modelPermissionsStore: ModelPermissionsStore): Props =
    Props(new ModelPermissionsStoreActor(modelPermissionsStore))

  sealed trait ModelPermissionsStoreRequest

  case class GetModelOverridesPermissions(modelId: String) extends ModelPermissionsStoreRequest
  case class SetModelOverridesPermissions(modelId: String, overridesPermissions: Boolean) extends ModelPermissionsStoreRequest
  case class GetModelPermissions(modelId: String) extends ModelPermissionsStoreRequest
  case class GetModelWorldPermissions(modelId: String) extends ModelPermissionsStoreRequest
  case class SetModelWorldPermissions(modelId: String, permissions: ModelPermissions) extends ModelPermissionsStoreRequest
  case class GetAllModelUserPermissions(modelId: String) extends ModelPermissionsStoreRequest
  case class GetModelUserPermissions(modelId: String, userId: DomainUserId) extends ModelPermissionsStoreRequest
  case class SetModelUserPermissions(modelId: String, userId: DomainUserId, permissions: ModelPermissions) extends ModelPermissionsStoreRequest
  case class RemoveModelUserPermissions(modelId: String, userId: DomainUserId) extends ModelPermissionsStoreRequest

  case class ModelUserPermissions(userId: DomainUserId, permissions: ModelPermissions)
  case class ModelPermissionsResponse(overrideWorld: Boolean, worldPermissions: ModelPermissions, userPermissions: List[ModelUserPermissions])
}

class ModelPermissionsStoreActor private[datastore] (
  private[this] val modelPermissionsStore: ModelPermissionsStore)
    extends StoreActor with ActorLogging {
  
  import ModelPermissionsStoreActor._

  def receive: Receive = {
    case GetModelOverridesPermissions(modelId) =>
      modelOverridesCollectionPermissions(modelId)
    case SetModelOverridesPermissions(modelId, overridesPermissions) =>
      setModelOverridesCollectionPermissions(modelId, overridesPermissions)
    case GetModelPermissions(modelId) =>
      getModelPermissions(modelId)
    case GetModelWorldPermissions(modelId) =>
      getModelWorldPermissions(modelId)
    case SetModelWorldPermissions(modelId, permissions) =>
      setModelWorldPermissions(modelId, permissions)
    case GetAllModelUserPermissions(modelId) =>
      getAllModelUserPermissions(modelId)
    case GetModelUserPermissions(modelId, userId) =>
      getModelUserPermissions(modelId, userId)
    case SetModelUserPermissions(modelId, userId, permissions: ModelPermissions) =>
      setModelUserPermissions(modelId, userId, permissions)
    case RemoveModelUserPermissions(modelId, userId) =>
      removeModelUserPermissions(modelId, userId)

    case message: Any => unhandled(message)
  }

  def getModelPermissions(modelId: String): Unit = {
    val result = for {
      overrideWorld <- modelPermissionsStore.modelOverridesCollectionPermissions(modelId)
      worldPermissions <- modelPermissionsStore.getModelWorldPermissions(modelId)
      userPermissions <- modelPermissionsStore.getAllModelUserPermissions(modelId)
    } yield {
      val userPermissionsList = userPermissions.toList.map {
        case Tuple2(userId, permissions) => ModelUserPermissions(userId, permissions)
      }
      ModelPermissionsResponse(overrideWorld, worldPermissions, userPermissionsList)
    }
    reply(result)
  }

  def modelOverridesCollectionPermissions(modelId: String): Unit = {
    reply(modelPermissionsStore.modelOverridesCollectionPermissions(modelId))
  }

  def setModelOverridesCollectionPermissions(modelId: String, overridePermissions: Boolean): Unit = {
    reply(modelPermissionsStore.setOverrideCollectionPermissions(modelId, overridePermissions))
  }

  def getModelWorldPermissions(modelId: String): Unit = {
    reply(modelPermissionsStore.getModelWorldPermissions(modelId))
  }

  def setModelWorldPermissions(modelId: String, permissions: ModelPermissions): Unit = {
    reply(modelPermissionsStore.setModelWorldPermissions(modelId, permissions))
  }

  def getAllModelUserPermissions(modelId: String): Unit = {
    reply(modelPermissionsStore.getAllModelUserPermissions(modelId).map(_.toList.map {
      case Tuple2(username, permissions) => ModelUserPermissions(username, permissions)
    }))
  }

  def getModelUserPermissions(modelId: String, userId: DomainUserId): Unit = {
    reply(modelPermissionsStore.getModelUserPermissions(modelId, userId))
  }

  def setModelUserPermissions(modelId: String, userId: DomainUserId, permissions: ModelPermissions): Unit = {
    reply(modelPermissionsStore.updateModelUserPermissions(modelId, userId, permissions))
  }

  def removeModelUserPermissions(modelId: String, userId: DomainUserId): Unit = {
    reply(modelPermissionsStore.removeModelUserPermissions(modelId, userId))
  }
}
