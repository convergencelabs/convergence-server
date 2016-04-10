package com.convergencelabs.server.domain

import scala.concurrent.duration.FiniteDuration
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.ot.Operation
import akka.actor.ActorRef
import java.time.Instant
import java.time.Duration
import com.convergencelabs.server.domain.model.data.ObjectValue

package model {

  //
  // Incoming Messages From Client
  //
  case class OpenRequestRecord(clientActor: ActorRef, askingActor: ActorRef)
  case class OpenRealtimeModelRequest(userId: String, sessionId: String, modelFqn: ModelFqn, initializerProvided: Boolean, clientActor: ActorRef)
  case class CreateModelRequest(modelFqn: ModelFqn, modelData: ObjectValue)
  case class DeleteModelRequest(modelFqn: ModelFqn)
  case class CloseRealtimeModelRequest(userId: String, sessionId: String)
  case class OperationSubmission(seqNo: Long, contextVersion: Long, operation: Operation)
  case class ClientModelDataResponse(modelData: ObjectValue)

  sealed trait ModelReferenceEvent {
    val id: String
  }

  case class PublishReference(id: String, key: String, referenceType: ReferenceType.Value) extends ModelReferenceEvent
  case class SetReference(id: String, key: String, referenceType: ReferenceType.Value, value: Any, contextVersion: Long) extends ModelReferenceEvent
  case class ClearReference(id: String, key: String) extends ModelReferenceEvent
  case class UnpublishReference(id: String, key: String) extends ModelReferenceEvent

  sealed trait DeleteModelResponse
  case object ModelDeleted extends DeleteModelResponse
  case object ModelNotFound extends DeleteModelResponse

  sealed trait CreateModelResponse
  case object ModelCreated extends CreateModelResponse
  case object ModelAlreadyExists extends CreateModelResponse

  case class CreateCollectionRequest(collection: Collection)
  case class UpdateCollectionRequest(collection: Collection)
  case class DeleteCollectionRequest(collectionId: String)
  case class GetCollectionRequest(collectionId: String)
  case object GetCollectionsRequest

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
    modelData: ObjectValue) extends OpenModelResponse

  case class ReferenceState(
    sessionId: String,
    valueId: String,
    key: String,
    referenceType: ReferenceType.Value,
    value: Option[Any])

  sealed trait OpenModelFailure extends OpenModelResponse
  case object ModelAlreadyOpen extends OpenModelFailure
  case object ModelDeletedWhileOpening extends OpenModelFailure
  case object NoSuchModel extends OpenModelFailure
  case class ClientDataRequestFailure(message: String) extends OpenModelFailure

  case class ModelShutdownRequest(modelFqn: ModelFqn)
  case class CloseRealtimeModelSuccess()

  trait RealtimeModelClientMessage
  case class OperationAcknowledgement(resourceId: String, seqNo: Long, contextVersion: Long) extends RealtimeModelClientMessage
  case class OutgoingOperation(
    resourceId: String,
    userId: String,
    sessionId: String,
    contextVersion: Long,
    timestampe: Long,
    operation: Operation) extends RealtimeModelClientMessage
  case class RemoteClientClosed(resourceId: String, sk: SessionKey) extends RealtimeModelClientMessage
  case class RemoteClientOpened(resourceId: String, sk: SessionKey) extends RealtimeModelClientMessage
  case class ModelForceClose(resourceId: String, reason: String) extends RealtimeModelClientMessage
  case class ClientModelDataRequest(modelFqn: ModelFqn) extends RealtimeModelClientMessage

  sealed trait RemoteReferenceEvent extends RealtimeModelClientMessage
  case class RemoteReferencePublished(resourceId: String, sessionId: String, id: String, key: String,
    referenceType: ReferenceType.Value) extends RemoteReferenceEvent
  case class RemoteReferenceSet(resourceId: String, sessionId: String, id: String, key: String,
    referenceType: ReferenceType.Value, value: Any) extends RemoteReferenceEvent
  case class RemoteReferenceCleared(resourceId: String, sessionId: String, id: String, key: String) extends RemoteReferenceEvent
  case class RemoteReferenceUnpublished(resourceId: String, sessionId: String, id: String, key: String) extends RemoteReferenceEvent

  case object ModelNotOpened
}
