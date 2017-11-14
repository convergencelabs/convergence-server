package com.convergencelabs.server.domain

import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.domain.model.Collection
import com.convergencelabs.server.domain.model.ModelSnapshotMetaData
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.ot.Operation

import akka.actor.ActorRef

package model {

  case class ModelFqn(domainFqn: DomainFqn, modelId: String)
  
  case class ClientAutoCreateModelConfigResponse(collectionId: String, modelData: Option[ObjectValue], overridePermissions: Option[Boolean],
    worldPermissions: Option[ModelPermissions], userPermissions: Option[Map[String, ModelPermissions]], ephemeral: Option[Boolean])

  case class ModelNotFoundException(modelId: String) extends Exception(s"A model with id '${modelId}' does not exist.")
  case class ModelAlreadyExistsException(modelId: String) extends Exception(s"A model with id '${modelId}' already exists.")

  case class CreateCollectionRequest(collection: Collection)
  case class UpdateCollectionRequest(collection: Collection)
  case class DeleteCollectionRequest(collectionId: String)
  case class GetCollectionRequest(collectionId: String)
  case object GetCollectionsRequest

  case class ModelAlreadyOpenException() extends RuntimeException()
  case class ModelNotOpenException() extends RuntimeException()
  case class ModelDeletedWhileOpeningException() extends RuntimeException()
  case class ClientDataRequestFailure(message: String) extends RuntimeException(message)
  
  case class ModelShutdownRequest(modelId: String, ephemeral: Boolean)
  
  case class ReferenceState(
    sessionId: String,
    valueId: Option[String],
    key: String,
    referenceType: ReferenceType.Value,
    values: List[Any])
}
