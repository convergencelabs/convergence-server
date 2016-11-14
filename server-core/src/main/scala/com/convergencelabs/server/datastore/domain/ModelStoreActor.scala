package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.domain.model.ModelFqn

import ModelStoreActor.GetModel
import ModelStoreActor.GetModels
import ModelStoreActor.GetModelsInCollection
import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.datastore.ModelStoreActor.DeleteModel
import com.convergencelabs.server.datastore.ModelStoreActor.CreateModel
import com.convergencelabs.server.datastore.ModelStoreActor.CreateOrUpdateModel
import com.convergencelabs.server.domain.model.data.ObjectValue
import apple.laf.JRSUIConstants.DoubleValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.NullValue
import scala.util.Success
import com.convergencelabs.server.datastore.domain.CollectionStore

object ModelStoreActor {
  def props(
    modelStore: ModelStore,
    collectionStore: CollectionStore): Props =
    Props(new ModelStoreActor(modelStore, collectionStore))

  trait ModelStoreRequest

  case class CreateOrUpdateModel(collectionId: String, modelId: String, data: Map[String, Any]) extends ModelStoreRequest
  case class CreateModel(collectionId: String, data: Map[String, Any]) extends ModelStoreRequest

  case class GetModels(offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest
  case class GetModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest
  case class GetModel(modelFqn: ModelFqn) extends ModelStoreRequest

  case class DeleteModel(modelFqn: ModelFqn) extends ModelStoreRequest

  case class CollectionInfo(id: String, name: String)
}

class ModelStoreActor private[datastore] (
  private[this] val modelStore: ModelStore,
  private[this] val collectionStore: CollectionStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case GetModels(offset, limit) =>
      getModels(offset, limit)
    case GetModelsInCollection(collectionId, offset, limit) =>
      getModelsInCollection(collectionId, offset, limit)
    case GetModel(modelFqn) =>
      getModel(modelFqn)
    case DeleteModel(modelFqn) =>
      deleteModel(modelFqn)
    case CreateModel(collectionId, data) =>
      createModel(collectionId, data)
    case CreateOrUpdateModel(collectionId, modelId, data) =>
      createOrUpdateModel(collectionId, modelId, data)

    case message: Any => unhandled(message)
  }

  def getModels(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(modelStore.getAllModelMetaData(offset, limit))
  }

  def getModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]): Unit = {
    reply(modelStore.getAllModelMetaDataInCollection(collectionId, offset, limit))
  }

  def getModel(modelFqn: ModelFqn): Unit = {
    reply(modelStore.getModel(modelFqn))
  }

  def createModel(collectionId: String, data: Map[String, Any]): Unit = {
    val root = ModelDataGenerator(data)
    val result = collectionStore.ensureCollectionExists(collectionId) flatMap { _ =>
      modelStore.createModel(collectionId, None, root) map {
        case CreateSuccess(model) => CreateSuccess(model.metaData.fqn)
        case x: Any => x
      }
    }
    reply(result)
  }

  def createOrUpdateModel(collectionId: String, modelId: String, data: Map[String, Any]): Unit = {
    //FIXME If the model is open this could cause problems.
    val root = ModelDataGenerator(data)
    val result = collectionStore.ensureCollectionExists(collectionId) flatMap { _ =>
      modelStore.createModel(collectionId, Some(modelId), root) flatMap {
        case CreateSuccess(model) => Success(CreateSuccess(model.metaData.fqn))
        case DuplicateValue =>
          modelStore.updateModel(ModelFqn(collectionId, modelId), root)
        case InvalidValue => Success(InvalidValue)
      }
    }
    reply(result)
  }

  def deleteModel(modelFqn: ModelFqn): Unit = {
    // FIXME If the model is open this could cause problems.
    reply(modelStore.deleteModel(modelFqn))
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
      case obj: Map[_, _] =>
        val children = obj map {
          case (k, v) => (k.toString, this.map(v))
        }
        ObjectValue(nextId(), children)
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
      case null =>
        NullValue(nextId())
    }
  }

  private[this] def nextId(): String = {
    id = id + 1
    this.ServerIdPrefix + id
  }
}
