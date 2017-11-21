package com.convergencelabs.server.datastore.domain

import scala.util.Success

import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue

import ModelStoreActor.GetModels
import ModelStoreActor.GetModelsInCollection
import akka.actor.ActorLogging
import akka.actor.Props
import java.time.Instant
import com.convergencelabs.server.domain.model.GetModelPermissionsRequest
import com.convergencelabs.server.domain.model.ModelCreator
import scala.util.Failure
import com.convergencelabs.server.datastore.StoreActor

object ModelStoreActor {
  def RelativePath = "ModelStoreActor"
  
  def props(
    persistenceProvider: DomainPersistenceProvider): Props =
    Props(new ModelStoreActor(persistenceProvider))

  trait ModelStoreRequest
  case class GetModels(offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest
  case class GetModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest
}

// FIXME merge this with the model query actor.

class ModelStoreActor private[datastore] (private[this] val persistenceProvider: DomainPersistenceProvider)
    extends StoreActor with ActorLogging {

  import ModelStoreActor._

  def receive: Receive = {
    case GetModels(offset, limit) =>
      getModels(offset, limit)
    case GetModelsInCollection(collectionId, offset, limit) =>
      getModelsInCollection(collectionId, offset, limit)

    case message: Any =>
      unhandled(message)
  }

  def getModels(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(persistenceProvider.modelStore.getAllModelMetaData(offset, limit))
  }

  def getModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]): Unit = {
    reply(persistenceProvider.modelStore.getAllModelMetaDataInCollection(collectionId, offset, limit))
  }
}
