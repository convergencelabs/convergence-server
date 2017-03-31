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

  case class ModelPermissionsResponse(overrideWorld: Boolean, worldPermissions: ModelPermissions, userPermissions: Map[String, ModelPermissions])
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
      overrideWorld <- modelPermissionsStore.modelOverridesCollectionPermissions(modelFqn)
      worldPermissions <- modelPermissionsStore.getModelWorldPermissions(modelFqn)
      userPermissions <- modelPermissionsStore.getAllModelUserPermissions(modelFqn)
    } yield {
      ModelPermissionsResponse(overrideWorld, worldPermissions, userPermissions)
    }
    reply(result)
  }

  def modelOverridesCollectionPermissions(modelFqn: ModelFqn): Unit = {
    reply(modelPermissionsStore.modelOverridesCollectionPermissions(modelFqn))
  }

  def setModelOverridesCollectionPermissions(modelFqn: ModelFqn, overridePermissions: Boolean): Unit = {
    reply(modelPermissionsStore.setOverrideCollectionPermissions(modelFqn, overridePermissions))
  }

  def getModelWorldPermissions(modelFqn: ModelFqn): Unit = {
    reply(modelPermissionsStore.getModelWorldPermissions(modelFqn))
  }

  def setModelWorldPermissions(modelFqn: ModelFqn, permissions: ModelPermissions): Unit = {
    reply(modelPermissionsStore.setModelWorldPermissions(modelFqn, permissions))
  }

  def getAllModelUserPermissions(modelFqn: ModelFqn): Unit = {
    reply(modelPermissionsStore.getAllModelUserPermissions(modelFqn))
  }

  def getModelUserPermissions(modelFqn: ModelFqn, username: String): Unit = {
    reply(modelPermissionsStore.getModelUserPermissions(modelFqn, username))
  }

  def setModelUserPermissions(modelFqn: ModelFqn, username: String, permissions: ModelPermissions): Unit = {
    reply(modelPermissionsStore.updateModelUserPermissions(modelFqn, username, permissions))
  }

  def removeModelUserPermissions(modelFqn: ModelFqn, username: String): Unit = {
    reply(modelPermissionsStore.removeModelUserPermissions(modelFqn, username))
  }
}
