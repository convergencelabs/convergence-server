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

  sealed trait DeleteModelResponse
  case object ModelDeleted extends DeleteModelResponse
  case object ModelNotFound extends DeleteModelResponse

  sealed trait CreateModelResponse
  case object ModelCreated extends CreateModelResponse
  case object ModelAlreadyExists extends CreateModelResponse

  //
  // Incoming Messages From Self
  //
  case class DatabaseModelResponse(modelData: Model, snapshotMetaData: ModelSnapshotMetaData)
  case class DatabaseModelFailure(cause: Throwable)

  //
  // Outgoing Messages
  //
  sealed trait OpenModelResponse
  case class OpenModelSuccess(realtimeModelActor: ActorRef, modelResourceId: String, metaData: OpenModelMetaData, modelData: JValue) extends OpenModelResponse

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
  case class RemoteClientClosed(resourceId: String, userId: String, sessionId: String) extends RealtimeModelClientMessage
  case class RemoteClientOpened(resourceId: String, userId: String, sessionId: String) extends RealtimeModelClientMessage
  case class ModelForceClose(resourceId: String, reason: String) extends RealtimeModelClientMessage
  case class ClientModelDataRequest(modelFqn: ModelFqn) extends RealtimeModelClientMessage

  case object ModelNotOpened
}
