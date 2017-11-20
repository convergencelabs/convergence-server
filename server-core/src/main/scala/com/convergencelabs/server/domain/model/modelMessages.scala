package com.convergencelabs.server.domain.model

import akka.actor.ActorRef
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.model.ot.Operation
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.domain.model.data.ObjectValue

sealed trait ModelMessage {
  val domainFqn: DomainFqn
  val modelId: String
}

//
// Messages that apply when the model is open or closed.
//
sealed trait StatelessModelMessage extends ModelMessage

// Basic Model CRUD
case class GetRealtimeModel(domainFqn: DomainFqn, modelId: String, sk: Option[SessionKey]) extends StatelessModelMessage
case class CreateOrUpdateRealtimeModel(
    domainFqn: DomainFqn,
    modelId: String, 
    collectionId: String,
    data: ObjectValue, 
    overridePermissions: Option[Boolean], 
    worldPermissions: Option[ModelPermissions], 
    userPermissions: Option[Map[String, ModelPermissions]],
    sk: Option[SessionKey]) extends StatelessModelMessage
    
case class CreateRealtimeModel(
    domainFqn: DomainFqn,
    modelId: String,
    collectionId: String, 
    data: ObjectValue, 
    overridePermissions: Option[Boolean], 
    worldPermissions: Option[ModelPermissions], 
    userPermissions: Option[Map[String, ModelPermissions]],
    sk: Option[SessionKey]) extends StatelessModelMessage

case class DeleteRealtimeModel(domainFqn: DomainFqn, modelId: String, sk: Option[SessionKey]) extends StatelessModelMessage

// Incoming Permissions Messages
case class GetModelPermissionsRequest(domainFqn: DomainFqn, modelId: String, sk: SessionKey) extends StatelessModelMessage
case class SetModelPermissionsRequest(
  domainFqn: DomainFqn,
  modelId: String,
  sk: SessionKey,
  overrideCollection: Option[Boolean],
  worldPermissions: Option[ModelPermissions],
  setAllUsers: Boolean,
  userPermissions: Map[String, Option[ModelPermissions]]) extends StatelessModelMessage

//
// Messages targeted specifically at "open" models.
//
sealed trait RealTimeModelMessage extends ModelMessage
case class OpenRealtimeModelRequest(domainFqn: DomainFqn, modelId: String, autoCreateId: Option[Integer], sk: SessionKey, clientActor: ActorRef) extends RealTimeModelMessage
case class CloseRealtimeModelRequest(domainFqn: DomainFqn, modelId: String, sk: SessionKey) extends RealTimeModelMessage
case class OperationSubmission(domainFqn: DomainFqn, modelId: String, seqNo: Long, contextVersion: Long, operation: Operation) extends RealTimeModelMessage

sealed trait ModelReferenceEvent extends RealTimeModelMessage {
  val id: Option[String]
}

case class PublishReference(domainFqn: DomainFqn, modelId: String, id: Option[String], key: String, referenceType: ReferenceType.Value, values: Option[List[Any]], contextVersion: Option[Long]) extends ModelReferenceEvent
case class SetReference(domainFqn: DomainFqn, modelId: String, id: Option[String], key: String, referenceType: ReferenceType.Value, values: List[Any], contextVersion: Long) extends ModelReferenceEvent
case class ClearReference(domainFqn: DomainFqn, modelId: String, id: Option[String], key: String) extends ModelReferenceEvent
case class UnpublishReference(domainFqn: DomainFqn, modelId: String, id: Option[String], key: String) extends ModelReferenceEvent

sealed trait InternalRealTimeModelMessage

//
// Outgoing Messages to the client actor
//  


case class OpenModelSuccess(
    valuePrefix: Long,
    metaData: OpenModelMetaData,
    connectedClients: Set[SessionKey],
    referencesBySession: Set[ReferenceState],
    modelData: ObjectValue,
    modelPermissions: ModelPermissions)
    
case class GetModelPermissionsResponse(overridesCollection: Boolean, worlPermissions: ModelPermissions, userPermissions: Map[String, ModelPermissions])


trait RealtimeModelClientMessage {
  val modelId: String
}

case class OperationAcknowledgement(modelId: String, seqNo: Long, contextVersion: Long, timestamp: Long) extends RealtimeModelClientMessage
case class OutgoingOperation(
  modelId: String,
  sessionKey: SessionKey,
  contextVersion: Long,
  timestamp: Long,
  operation: Operation) extends RealtimeModelClientMessage
case class RemoteClientClosed(modelId: String, sk: SessionKey) extends RealtimeModelClientMessage
case class RemoteClientOpened(modelId: String, sk: SessionKey) extends RealtimeModelClientMessage
case class ModelForceClose(modelId: String, reason: String) extends RealtimeModelClientMessage
case class ModelPermissionsChanged(modelId: String, permissions: ModelPermissions) extends RealtimeModelClientMessage
case class ClientAutoCreateModelConfigRequest(modelId: String, autoConfigId: Integer) extends RealtimeModelClientMessage

sealed trait RemoteReferenceEvent extends RealtimeModelClientMessage
case class RemoteReferencePublished(
  modelId: String, sessionId: String, id: Option[String], key: String,
  referenceType: ReferenceType.Value, values: Option[List[Any]]) extends RemoteReferenceEvent
case class RemoteReferenceSet(modelId: String, sessionId: String, id: Option[String], key: String,
  referenceType: ReferenceType.Value, value: List[Any]) extends RemoteReferenceEvent
case class RemoteReferenceCleared(modelId: String, sessionId: String, id: Option[String], key: String) extends RemoteReferenceEvent
case class RemoteReferenceUnpublished(modelId: String, sessionId: String, id: Option[String], key: String) extends RemoteReferenceEvent
