package com.convergencelabs.server.datastore

import scala.util.Success

import com.convergencelabs.server.datastore.ModelStoreActor.CreateModel
import com.convergencelabs.server.datastore.ModelStoreActor.CreateOrUpdateModel
import com.convergencelabs.server.datastore.ModelStoreActor.DeleteModel
import com.convergencelabs.server.datastore.domain.CollectionStore
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue

import ModelStoreActor.GetModel
import ModelStoreActor.GetModels
import ModelStoreActor.GetModelsInCollection
import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.domain.model.data.DateValue
import java.time.Instant
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.domain.model.GetModelPermissionsRequest
import com.convergencelabs.server.domain.model.ModelCreator
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import scala.util.Failure

object ModelStoreActor {
  def props(
    persistenceProvider: DomainPersistenceProvider): Props =
    Props(new ModelStoreActor(persistenceProvider))

  trait ModelStoreRequest
  case class GetModels(offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest
  case class GetModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest

  case class CollectionInfo(id: String, name: String)
}

class ModelStoreActor private[datastore] (private[this] val persistenceProvider: DomainPersistenceProvider)
    extends StoreActor with ActorLogging {

  val modelCretor = new ModelCreator()

  def receive: Receive = {
    case GetModels(offset, limit) =>
      getModels(offset, limit)
    case GetModelsInCollection(collectionId, offset, limit) =>
      getModelsInCollection(collectionId, offset, limit)
    case GetModel(modelId) =>
      getModel(modelId)
    case DeleteModel(modelId) =>
      deleteModel(modelId)
    case CreateModel(collectionId, data, overridePermissions, worldPermissions, userPermissions) =>
      createModel(collectionId, data, overridePermissions, worldPermissions, userPermissions)
    case CreateOrUpdateModel(collectionId, modelId, data, overridePermissions, worldPermissions, userPermissions) =>
      createOrUpdateModel(collectionId, modelId, data, overridePermissions, worldPermissions, userPermissions)

    case message: Any => unhandled(message)
  }

  def getModels(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(persistenceProvider.modelStore.getAllModelMetaData(offset, limit))
  }

  def getModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]): Unit = {
    reply(persistenceProvider.modelStore.getAllModelMetaDataInCollection(collectionId, offset, limit))
  }

  

  def deleteModel(modelId: String): Unit = {
    // FIXME If the model is open this could cause problems.
    reply(persistenceProvider.modelStore.deleteModel(modelId))
  }
}
