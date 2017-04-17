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

  case class CreateOrUpdateModel(collectionId: Option[String], modelId: String, data: Map[String, Any], overridePermissions: Option[Boolean], worldPermissions: Option[ModelPermissions], userPermissions: Option[Map[String, ModelPermissions]]) extends ModelStoreRequest
  case class CreateModel(collectionId: String, data: Map[String, Any], overridePermissions: Option[Boolean], worldPermissions: Option[ModelPermissions], userPermissions: Option[Map[String, ModelPermissions]]) extends ModelStoreRequest

  case class GetModels(offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest
  case class GetModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest
  case class GetModel(modelId: String) extends ModelStoreRequest

  case class DeleteModel(modelId: String) extends ModelStoreRequest

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
    case GetModel(modelFqn) =>
      getModel(modelFqn)
    case DeleteModel(modelFqn) =>
      deleteModel(modelFqn)
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

  def getModel(modelId: String): Unit = {
    reply(persistenceProvider.modelStore.getModel(modelId))
  }

  def createModel(
    collectionId: String,
    data: Map[String, Any],
    overridePermissions: Option[Boolean],
    worldPermissions: Option[ModelPermissions],
    userPermissions: Option[Map[String, ModelPermissions]]): Unit = {
    val root = ModelDataGenerator(data)
    // FIXME should we have an owner
    val result = modelCretor.createModel(
      persistenceProvider,
      None,
      collectionId,
      None,
      root,
      overridePermissions,
      worldPermissions,
      userPermissions).map(model => model.metaData.modelId)

    reply(result)
  }

  def createOrUpdateModel(
    collectionId: Option[String],
    modelId: String,
    data: Map[String, Any],
    overridePermissions: Option[Boolean],
    worldPermissions: Option[ModelPermissions],
    userPermissions: Option[Map[String, ModelPermissions]]): Unit = {
    //FIXME If the model is open this could cause problems.
    val result = persistenceProvider.modelStore.modelExists(modelId) flatMap { exists =>
      if (exists) {
        val root = ModelDataGenerator(data)
        persistenceProvider.modelStore.updateModel(modelId, root, worldPermissions)
      } else {
        collectionId match {
          case Some(id) =>
            val root = ModelDataGenerator(data)
            modelCretor.createModel(
              persistenceProvider,
              None,
              id,
              Some(modelId),
              root,
              overridePermissions,
              worldPermissions,
              userPermissions)
          case None =>
            Failure(new IllegalArgumentException("A collection must be provided to create a model that does not exist"))
        }
      }
    }
    reply(result)
  }

  def deleteModel(modelId: String): Unit = {
    // FIXME If the model is open this could cause problems.
    reply(persistenceProvider.modelStore.deleteModel(modelId))
  }
}

object ModelDataGenerator {
  def apply(data: Map[String, Any]): ObjectValue = {
    val gen = new ModelDataGenerator()
    gen.create(data)
  }
}

class ModelDataGenerator() {
  val ServerIdPrefix = "0:";
  var id: Int = 0;

  def create(data: Map[String, Any]): ObjectValue = {
    map(data).asInstanceOf[ObjectValue]
  }

  private[this] def map(value: Any): DataValue = {
    value match {
      case obj: Map[Any, Any] =>
        if (obj.contains("$convergenceType")) {
          DateValue(nextId(), Instant.parse(obj.get("value").toString()))
        } else {
          val children = obj map {
            case (k, v) => (k.toString, this.map(v))
          }
          ObjectValue(nextId(), children)
        }
      case arr: List[_] =>
        val array = arr.map(v => this.map(v))
        ArrayValue(nextId(), array)
      case num: Double =>
        DoubleValue(nextId(), num)
      case num: Int =>
        DoubleValue(nextId(), num.doubleValue())
      case num: BigInt =>
        DoubleValue(nextId(), num.doubleValue())
      case num: Long =>
        DoubleValue(nextId(), num.doubleValue())
      case bool: Boolean =>
        BooleanValue(nextId(), bool)
      case str: String =>
        StringValue(nextId(), str)
      case date: Instant =>
        DateValue(nextId(), date)
      case null =>
        NullValue(nextId())
    }
  }

  private[this] def nextId(): String = {
    id = id + 1
    this.ServerIdPrefix + id
  }
}
