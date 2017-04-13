package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetAllModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelWorldPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.ModelPermissionsResponse
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.RemoveModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.SetModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.SetModelWorldPermissions
import com.convergencelabs.server.datastore.domain.CollectionStore
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore
import com.convergencelabs.server.domain.model.ModelFqn

import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelOverridesPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.SetModelOverridesPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.ModelUserPermissions

object ModelPermissionsStoreActor {
  def props(modelPermissionsStore: ModelPermissionsStore): Props =
    Props(new ModelPermissionsStoreActor(modelPermissionsStore))

  sealed trait ModelPermissionsStoreRequest

  case class GetModelOverridesPermissions(modelFqn: ModelFqn) extends ModelPermissionsStoreRequest
  case class SetModelOverridesPermissions(modelFqn: ModelFqn, overridesPermissions: Boolean) extends ModelPermissionsStoreRequest
  case class GetModelPermissions(modelFqn: ModelFqn) extends ModelPermissionsStoreRequest
  case class GetModelWorldPermissions(modelFqn: ModelFqn) extends ModelPermissionsStoreRequest
  case class SetModelWorldPermissions(modelFqn: ModelFqn, permissions: ModelPermissions) extends ModelPermissionsStoreRequest
  case class GetAllModelUserPermissions(modelFqn: ModelFqn) extends ModelPermissionsStoreRequest
  case class GetModelUserPermissions(modelFqn: ModelFqn, username: String) extends ModelPermissionsStoreRequest
  case class SetModelUserPermissions(modelFqn: ModelFqn, username: String, permissions: ModelPermissions) extends ModelPermissionsStoreRequest
  case class RemoveModelUserPermissions(modelFqn: ModelFqn, username: String) extends ModelPermissionsStoreRequest

  case class ModelUserPermissions(username: String, permissions: ModelPermissions)
  case class ModelPermissionsResponse(overrideWorld: Boolean, worldPermissions: ModelPermissions, userPermissions: List[ModelUserPermissions])
}

class ModelPermissionsStoreActor private[datastore] (
  private[this] val modelPermissionsStore: ModelPermissionsStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case GetModelOverridesPermissions(modelFqn) =>
      modelOverridesCollectionPermissions(modelFqn)
    case SetModelOverridesPermissions(modelFqn, overridesPermissions) =>
      setModelOverridesCollectionPermissions(modelFqn, overridesPermissions)
    case GetModelPermissions(modelFqn) =>
      getModelPermissions(modelFqn)
    case GetModelWorldPermissions(modelFqn) =>
      getModelWorldPermissions(modelFqn)
    case SetModelWorldPermissions(modelFqn, permissions) =>
      setModelWorldPermissions(modelFqn, permissions)
    case GetAllModelUserPermissions(modelFqn) =>
      getAllModelUserPermissions(modelFqn)
    case GetModelUserPermissions(modelFqn, username: String) =>
      getModelUserPermissions(modelFqn, username)
    case SetModelUserPermissions(modelFqn, username: String, permissions: ModelPermissions) =>
      setModelUserPermissions(modelFqn, username, permissions)
    case RemoveModelUserPermissions(modelFqn, username: String) =>
      removeModelUserPermissions(modelFqn, username)

    case message: Any => unhandled(message)
  }

  def getModelPermissions(modelFqn: ModelFqn): Unit = {
    val result = for {
      overrideWorld <- modelPermissionsStore.modelOverridesCollectionPermissions(modelFqn.modelId)
      worldPermissions <- modelPermissionsStore.getModelWorldPermissions(modelFqn.modelId)
      userPermissions <- modelPermissionsStore.getAllModelUserPermissions(modelFqn.modelId)
    } yield {
      val userPermissionsList = userPermissions.toList.map {
        case Tuple2(username, permissions) => ModelUserPermissions(username, permissions)
      }
      ModelPermissionsResponse(overrideWorld, worldPermissions, userPermissionsList)
    }
    reply(result)
  }

  def modelOverridesCollectionPermissions(modelFqn: ModelFqn): Unit = {
    reply(modelPermissionsStore.modelOverridesCollectionPermissions(modelFqn.modelId))
  }

  def setModelOverridesCollectionPermissions(modelFqn: ModelFqn, overridePermissions: Boolean): Unit = {
    reply(modelPermissionsStore.setOverrideCollectionPermissions(modelFqn.modelId, overridePermissions))
  }

  def getModelWorldPermissions(modelFqn: ModelFqn): Unit = {
    reply(modelPermissionsStore.getModelWorldPermissions(modelFqn.modelId))
  }

  def setModelWorldPermissions(modelFqn: ModelFqn, permissions: ModelPermissions): Unit = {
    reply(modelPermissionsStore.setModelWorldPermissions(modelFqn.modelId, permissions))
  }

  def getAllModelUserPermissions(modelFqn: ModelFqn): Unit = {
    reply(modelPermissionsStore.getAllModelUserPermissions(modelFqn.modelId).map(_.toList.map {
      case Tuple2(username, permissions) => ModelUserPermissions(username, permissions)
    }))
  }

  def getModelUserPermissions(modelFqn: ModelFqn, username: String): Unit = {
    reply(modelPermissionsStore.getModelUserPermissions(modelFqn.modelId, username))
  }

  def setModelUserPermissions(modelFqn: ModelFqn, username: String, permissions: ModelPermissions): Unit = {
    reply(modelPermissionsStore.updateModelUserPermissions(modelFqn.modelId, username, permissions))
  }

  def removeModelUserPermissions(modelFqn: ModelFqn, username: String): Unit = {
    reply(modelPermissionsStore.removeModelUserPermissions(modelFqn.modelId, username))
  }
}
