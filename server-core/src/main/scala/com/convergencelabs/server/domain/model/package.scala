package com.convergencelabs.server.domain

import scala.concurrent.duration.FiniteDuration
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.ot.Operation
import akka.actor.ActorRef
import java.time.Instant
import java.time.Duration
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.Collection
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelSnapshotMetaData
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.datastore.domain.CollectionPermissions

package model {

  //
  // Incoming Messages From Client
  //
  case class OpenRequestRecord(clientActor: ActorRef, askingActor: ActorRef)
  case class OpenRealtimeModelRequest(sk: SessionKey, modelId: Option[String], autoCreateId: Option[Integer], clientActor: ActorRef)
  case class CreateModelRequest(sk: SessionKey, collectionId: String, modelId: Option[String], modelData: ObjectValue,
                                overridePermissions: Option[Boolean], worldPermissions: Option[ModelPermissions])
  case class DeleteModelRequest(sk: SessionKey, modelFqn: ModelFqn)
  case class CloseRealtimeModelRequest(sk: SessionKey)
  case class OperationSubmission(seqNo: Long, contextVersion: Long, operation: Operation)
  case class ClientAutoCreateModelConfigResponse(collectionId: String, modelData: Option[ObjectValue], overridePermissions: Option[Boolean],
                                            worldPermissions: Option[ModelPermissions], userPermissions: Option[Map[String, ModelPermissions]])

  case class GetModelPermissionsRequest(modelId: String)

  case class GetModelPermissionsResponse(overridesCollection: Boolean, worlPermissions: ModelPermissions, userPermissions: Map[String, ModelPermissions])

  case class SetModelPermissionsRequest(
    sk: SessionKey,
    collectionId: String,
    modelId: String,
    overrideCollection: Option[Boolean],
    worldPermissions: Option[ModelPermissions],
    setAllUsers: Boolean,
    userPermissions: Map[String, Option[ModelPermissions]])

  sealed trait ModelReferenceEvent {
    val id: Option[String]
  }

  case class PublishReference(id: Option[String], key: String, referenceType: ReferenceType.Value, values: Option[List[Any]], contextVersion: Option[Long]) extends ModelReferenceEvent
  case class SetReference(id: Option[String], key: String, referenceType: ReferenceType.Value, values: List[Any], contextVersion: Long) extends ModelReferenceEvent
  case class ClearReference(id: Option[String], key: String) extends ModelReferenceEvent
  case class UnpublishReference(id: Option[String], key: String) extends ModelReferenceEvent

  case class ModelNotFoundException(message: String = "", cause: Throwable = null)
    extends Exception(message, cause)

  sealed trait DeleteModelResponse
  case object ModelDeleted extends DeleteModelResponse
  case object ModelNotFound extends DeleteModelResponse

  sealed trait CreateModelResponse
  case class ModelCreated(id: String) extends CreateModelResponse
  case object ModelAlreadyExists extends CreateModelResponse

  case class CreateCollectionRequest(collection: Collection)
  case class UpdateCollectionRequest(collection: Collection)
  case class DeleteCollectionRequest(collectionId: String)
  case class GetCollectionRequest(collectionId: String)
  case object GetCollectionsRequest

  // TODO this will also need to include the permissions from the collection as well
  // and we will need to trigger some sort of update when the collection wide
  // permissions change.
  case class RealTimeModelPermissions(
    modelOverridesCollection: Boolean,
    collectionWorld: CollectionPermissions,
    collectionUsers: Map[String, CollectionPermissions],
    modelWorld: ModelPermissions,
    modelUsers: Map[String, ModelPermissions])

  //
  // Incoming Messages From Self
  //
  case class DatabaseModelResponse(modelData: Model, snapshotMetaData: ModelSnapshotMetaData)
  case class DatabaseModelFailure(cause: Throwable)

  //
  // Outgoing Messages
  //
  sealed trait OpenModelResponse
  case class OpenModelSuccess(
    realtimeModelActor: ActorRef,
    modelResourceId: String,
    valuePrefix: String,
    metaData: OpenModelMetaData,
    connectedClients: Set[SessionKey],
    referencesBySession: Set[ReferenceState],
    modelData: ObjectValue,
    modelPermissions: ModelPermissions) extends OpenModelResponse

  case class ReferenceState(
    sessionId: String,
    valueId: Option[String],
    key: String,
    referenceType: ReferenceType.Value,
    values: List[Any])

  sealed trait OpenModelFailure extends OpenModelResponse
  case object ModelAlreadyOpen extends OpenModelFailure
  case object ModelDeletedWhileOpening extends OpenModelFailure
  case object NoSuchModel extends OpenModelFailure
  case class ClientDataRequestFailure(message: String) extends OpenModelFailure

  case class ModelShutdownRequest(modelId: String)
  case class CloseRealtimeModelSuccess()

  trait RealtimeModelClientMessage
  case class OperationAcknowledgement(resourceId: String, seqNo: Long, contextVersion: Long, timestamp: Long) extends RealtimeModelClientMessage
  case class OutgoingOperation(
    resourceId: String,
    sessionKey: SessionKey,
    contextVersion: Long,
    timestamp: Long,
    operation: Operation) extends RealtimeModelClientMessage
  case class RemoteClientClosed(resourceId: String, sk: SessionKey) extends RealtimeModelClientMessage
  case class RemoteClientOpened(resourceId: String, sk: SessionKey) extends RealtimeModelClientMessage
  case class ModelForceClose(resourceId: String, reason: String) extends RealtimeModelClientMessage
  case class ModelPermissionsChanged(resourceId: String, permissions: ModelPermissions) extends RealtimeModelClientMessage
  case class ClientAutoCreateModelConfigRequest(autoConfigId: Integer) extends RealtimeModelClientMessage

  sealed trait RemoteReferenceEvent extends RealtimeModelClientMessage
  case class RemoteReferencePublished(
    resourceId: String, sessionId: String, id: Option[String], key: String,
    referenceType: ReferenceType.Value, values: Option[List[Any]]) extends RemoteReferenceEvent
  case class RemoteReferenceSet(resourceId: String, sessionId: String, id: Option[String], key: String,
                                referenceType: ReferenceType.Value, value: List[Any]) extends RemoteReferenceEvent
  case class RemoteReferenceCleared(resourceId: String, sessionId: String, id: Option[String], key: String) extends RemoteReferenceEvent
  case class RemoteReferenceUnpublished(resourceId: String, sessionId: String, id: Option[String], key: String) extends RemoteReferenceEvent

  case object ModelNotOpened
}
