package com.convergencelabs.server.domain

import scala.concurrent.duration.FiniteDuration
import com.convergencelabs.server.datastore.domain.ModelData
import com.convergencelabs.server.datastore.domain.SnapshotMetaData
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.ot.ops.Operation
import akka.actor.ActorRef
import java.time.Instant
import java.time.Duration

package model {

  // Config
  case class SnapshotConfig(
      snapshotsEnabled: Boolean,
      triggerByVersion: Boolean,
      limitedByVersion: Boolean,
      minimumVersionInterval: Long,
      maximumVersionInterval: Long,
      triggerByTime: Boolean,
      limitedByTime: Boolean,
      minimumTimeInterval: Duration,
      maximumTimeInterval: Duration) {

    def snapshotRequired(
      previousVersion: Long,
      currentVersion: Long,
      previousTime: Instant,
      currentTime: Instant): scala.Boolean = {

      if (!snapshotsEnabled) {
        false
      } else {
        val versionInterval = currentVersion - previousVersion
        val allowedByVersion = !limitedByVersion || versionInterval >= minimumVersionInterval
        val requiredByVersion = versionInterval > maximumVersionInterval && triggerByVersion
  
        val timeInterval = Duration.between(previousTime, currentTime)
        val allowedByTime = !limitedByTime || timeInterval.compareTo(minimumTimeInterval) >= 0
        val requiredByTime = timeInterval.compareTo(maximumTimeInterval) > 0 && triggerByTime
  
        allowedByVersion && allowedByTime && (requiredByTime || requiredByVersion)
      }
    }
  }

  //
  // Data Classes
  //
  case class ModelFqn(collectionId: String, modelId: String)
  case class OpenModelMetaData(version: Long, createdTime: Instant, modifiedTime: Instant)

  //
  // Incoming Messages From Client
  //
  case class OpenRequestRecord(clientActor: ActorRef, askingActor: ActorRef)
  case class OpenRealtimeModelRequest(sessionId: String, modelFqn: ModelFqn, clientActor: ActorRef)
  case class CreateModelRequest(modelFqn: ModelFqn, modelData: JValue)
  case class DeleteModelRequest(modelFqn: ModelFqn)
  case class CloseRealtimeModelRequest(sessionId: String)
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
  case class DatabaseModelResponse(modelData: ModelData, snapshotMetaData: SnapshotMetaData)
  case class DatabaseModelFailure(cause: Throwable)

  //
  // Outgoing Messages
  //
  sealed trait OpenModelResponse
  case class OpenModelSuccess(realtimeModelActor: ActorRef, modelResourceId: String, metaData: OpenModelMetaData, modelData: JValue) extends OpenModelResponse
  case object ModelAlreadyOpen extends OpenModelResponse

  case class ModelShutdownRequest(modelFqn: ModelFqn)
  case class CloseRealtimeModelSuccess()

  trait RealtimeModelClientMessage
  case class OperationAcknowledgement(resourceId: String, seqNo: Long, contextVersion: Long) extends RealtimeModelClientMessage
  case class OutgoingOperation(resourceId: String, clientId: String, contextVersion: Long, timestampe: Long, operation: Operation) extends RealtimeModelClientMessage
  case class RemoteClientClosed(resourceId: String, clientId: String) extends RealtimeModelClientMessage
  case class RemoteClientOpened(resourceId: String, clientId: String) extends RealtimeModelClientMessage
  case class ModelForceClose(resourceId: String, clientId: String, reason: String) extends RealtimeModelClientMessage
  case class ClientModelDataRequest(modelFqn: ModelFqn) extends RealtimeModelClientMessage

  
  case object ModelNotOpened
}