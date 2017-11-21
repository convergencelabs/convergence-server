package com.convergencelabs.server.datastore.domain


import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.datastore.StoreActor

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
  case class GetModelUserPermissions(modelId: String, username: String) extends ModelPermissionsStoreRequest
  case class SetModelUserPermissions(modelId: String, username: String, permissions: ModelPermissions) extends ModelPermissionsStoreRequest
  case class RemoveModelUserPermissions(modelId: String, username: String) extends ModelPermissionsStoreRequest

  case class ModelUserPermissions(username: String, permissions: ModelPermissions)
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
    case GetModelUserPermissions(modelId, username: String) =>
      getModelUserPermissions(modelId, username)
    case SetModelUserPermissions(modelId, username: String, permissions: ModelPermissions) =>
      setModelUserPermissions(modelId, username, permissions)
    case RemoveModelUserPermissions(modelId, username: String) =>
      removeModelUserPermissions(modelId, username)

    case message: Any => unhandled(message)
  }

  def getModelPermissions(modelId: String): Unit = {
    val result = for {
      overrideWorld <- modelPermissionsStore.modelOverridesCollectionPermissions(modelId)
      worldPermissions <- modelPermissionsStore.getModelWorldPermissions(modelId)
      userPermissions <- modelPermissionsStore.getAllModelUserPermissions(modelId)
    } yield {
      val userPermissionsList = userPermissions.toList.map {
        case Tuple2(username, permissions) => ModelUserPermissions(username, permissions)
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

  def getModelUserPermissions(modelId: String, username: String): Unit = {
    reply(modelPermissionsStore.getModelUserPermissions(modelId, username))
  }

  def setModelUserPermissions(modelId: String, username: String, permissions: ModelPermissions): Unit = {
    reply(modelPermissionsStore.updateModelUserPermissions(modelId, username, permissions))
  }

  def removeModelUserPermissions(modelId: String, username: String): Unit = {
    reply(modelPermissionsStore.removeModelUserPermissions(modelId, username))
  }
}
