package com.convergencelabs.server.domain.model

import java.time.Instant

import akka.actor.ActorRef
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.domain.{DomainId, DomainUserId, DomainUserSessionId}
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.ot.Operation

sealed trait ModelMessage {
  val domainFqn: DomainId
  val modelId: String
}

//
// Messages that apply when the model is open or closed.
//
sealed trait StatelessModelMessage extends ModelMessage

// Basic Model CRUD
case class GetRealtimeModel(domainFqn: DomainId, modelId: String, session: Option[DomainUserSessionId]) extends StatelessModelMessage
case class CreateOrUpdateRealtimeModel(
    domainFqn: DomainId,
    modelId: String, 
    collectionId: String,
    data: ObjectValue, 
    overridePermissions: Option[Boolean], 
    worldPermissions: Option[ModelPermissions], 
    userPermissions: Option[Map[DomainUserId, ModelPermissions]],
    session: Option[DomainUserSessionId]) extends StatelessModelMessage
    
case class CreateRealtimeModel(
    domainFqn: DomainId,
    modelId: String,
    collectionId: String, 
    data: ObjectValue, 
    overridePermissions: Option[Boolean], 
    worldPermissions: Option[ModelPermissions], 
    userPermissions: Map[DomainUserId, ModelPermissions],
    session: Option[DomainUserSessionId]) extends StatelessModelMessage

case class DeleteRealtimeModel(domainFqn: DomainId, modelId: String, session: Option[DomainUserSessionId]) extends StatelessModelMessage

// Incoming Permissions Messages
case class GetModelPermissionsRequest(domainFqn: DomainId, modelId: String, session: DomainUserSessionId) extends StatelessModelMessage
case class SetModelPermissionsRequest(
                                       domainFqn: DomainId,
                                       modelId: String,
                                       session: DomainUserSessionId,
                                       overrideCollection: Option[Boolean],
                                       worldPermissions: Option[ModelPermissions],
                                       setAllUsers: Boolean,
                                       addedUserPermissions: Map[DomainUserId, ModelPermissions],
                                       removedUserPermissions: List[DomainUserId]) extends StatelessModelMessage

//
// Messages targeted specifically at "open" models.
//
sealed trait RealTimeModelMessage extends ModelMessage
case class OpenRealtimeModelRequest(
                                     domainFqn: DomainId,
                                     modelId: String,
                                     autoCreateId: Option[Int],
                                     reconnect: Option[ReconnectRequest],
                                     session: DomainUserSessionId,
                                     clientActor: ActorRef) extends RealTimeModelMessage
case class ReconnectRequest(sessionId: String, contextVersion: Long)
case class CloseRealtimeModelRequest(domainFqn: DomainId, modelId: String, session: DomainUserSessionId) extends RealTimeModelMessage
case class OperationSubmission(domainFqn: DomainId, modelId: String, seqNo: Long, contextVersion: Long, operation: Operation) extends RealTimeModelMessage

sealed trait ModelReferenceEvent extends RealTimeModelMessage {
  val id: Option[String]
}

case class ShareReference(domainFqn: DomainId, modelId: String, id: Option[String], key: String, referenceType: ReferenceType.Value, values: List[Any], contextVersion: Long) extends ModelReferenceEvent
case class SetReference(domainFqn: DomainId, modelId: String, id: Option[String], key: String, referenceType: ReferenceType.Value, values: List[Any], contextVersion: Long) extends ModelReferenceEvent
case class ClearReference(domainFqn: DomainId, modelId: String, id: Option[String], key: String) extends ModelReferenceEvent
case class UnshareReference(domainFqn: DomainId, modelId: String, id: Option[String], key: String) extends ModelReferenceEvent

sealed trait InternalRealTimeModelMessage

//
// Outgoing Messages to the client actor
//  


case class OpenModelSuccess(
    valuePrefix: Long,
    metaData: OpenModelMetaData,
    connectedClients: Set[DomainUserSessionId],
    referencesBySession: Set[ReferenceState],
    modelData: ObjectValue,
    modelPermissions: ModelPermissions)
    
case class GetModelPermissionsResponse(overridesCollection: Boolean, worlPermissions: ModelPermissions, userPermissions: Map[DomainUserId, ModelPermissions])

trait RealtimeModelClientMessage {
  val modelId: String
}

case class OperationAcknowledgement(modelId: String, seqNo: Int, contextVersion: Int, timestamp: Instant) extends RealtimeModelClientMessage
case class OutgoingOperation(
  modelId: String,
  session: DomainUserSessionId,
  contextVersion: Int,
  timestamp: Instant,
  operation: Operation) extends RealtimeModelClientMessage
case class RemoteClientClosed(modelId: String, session: DomainUserSessionId) extends RealtimeModelClientMessage
case class RemoteClientOpened(modelId: String, session: DomainUserSessionId) extends RealtimeModelClientMessage
case class ModelForceClose(modelId: String, reason: String) extends RealtimeModelClientMessage
case class ModelPermissionsChanged(modelId: String, permissions: ModelPermissions) extends RealtimeModelClientMessage
case class ClientAutoCreateModelConfigRequest(modelId: String, autoConfigId: Integer) extends RealtimeModelClientMessage

sealed trait RemoteReferenceEvent extends RealtimeModelClientMessage
case class RemoteReferenceShared(
  modelId: String, session: DomainUserSessionId, id: Option[String], key: String,
  referenceType: ReferenceType.Value, values: List[Any]) extends RemoteReferenceEvent
case class RemoteReferenceSet(modelId: String, session: DomainUserSessionId, id: Option[String], key: String,
  referenceType: ReferenceType.Value, value: List[Any]) extends RemoteReferenceEvent
case class RemoteReferenceCleared(modelId: String, session: DomainUserSessionId, id: Option[String], key: String) extends RemoteReferenceEvent
case class RemoteReferenceUnshared(modelId: String, session: DomainUserSessionId, id: Option[String], key: String) extends RemoteReferenceEvent
