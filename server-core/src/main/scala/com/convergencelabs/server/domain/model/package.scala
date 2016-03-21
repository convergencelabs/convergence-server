package com.convergencelabs.server.domain

import scala.concurrent.duration.FiniteDuration
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.ot.Operation
import akka.actor.ActorRef
import java.time.Instant
import java.time.Duration

package model {

  //
  // Incoming Messages From Client
  //
  case class OpenRequestRecord(clientActor: ActorRef, askingActor: ActorRef)
  case class OpenRealtimeModelRequest(userId: String, sessionId: String, modelFqn: ModelFqn, initializerProvided: Boolean, clientActor: ActorRef)
  case class CreateModelRequest(modelFqn: ModelFqn, modelData: JValue)
  case class DeleteModelRequest(modelFqn: ModelFqn)
  case class CloseRealtimeModelRequest(userId: String, sessionId: String)
  case class OperationSubmission(seqNo: Long, contextVersion: Long, operation: Operation)
  case class ClientModelDataResponse(modelData: JValue)

  sealed trait ModelReferenceEvent
  case class PublishReference(path: List[Any], key: String, referenceType: ReferenceType.Value) extends ModelReferenceEvent
  case class SetReference(path: List[Any], key: String, referenceType: ReferenceType.Value, value: JValue) extends ModelReferenceEvent
  case class ClearReference(path: List[Any], key: String) extends ModelReferenceEvent
  case class UnpublishReference(path: List[Any], key: String) extends ModelReferenceEvent

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
    metaData: OpenModelMetaData,
    connectedClients: Set[SessionKey],
    referencesBySession: Map[SessionKey, Set[ReferenceState]],
    modelData: JValue) extends OpenModelResponse

  case class ReferenceState(
    path: List[Any],
    key: String,
    referenceType: ReferenceType.Value,
    value: Option[JValue])

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

  case class RemoteReferencePublished(resourceId: String, sessionId: String, path: List[Any], key: String, referenceType: ReferenceType.Value) extends RealtimeModelClientMessage
  case class RemoteReferenceSet(resourceId: String, sessionId: String, path: List[Any], key: String, referenceType: ReferenceType.Value, value: JValue) extends RealtimeModelClientMessage
  case class RemoteReferenceCleared(resourceId: String, sessionId: String, path: List[Any], key: String) extends RealtimeModelClientMessage
  case class RemoteReferenceUnpublished(resourceId: String, sessionId: String, path: List[Any], key: String) extends RealtimeModelClientMessage

  case object ModelNotOpened
}
