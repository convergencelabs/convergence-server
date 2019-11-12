/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.model

import java.time.Instant

import akka.actor.ActorRef
import com.convergencelabs.convergence.server.datastore.domain.ModelPermissions
import com.convergencelabs.convergence.server.domain.model.data.ObjectValue
import com.convergencelabs.convergence.server.domain.model.ot.Operation
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId, DomainUserSessionId}

sealed trait ModelMessage {
  val domainId: DomainId
  val modelId: String
}

//
// Messages that apply when the model is open or closed.
//
sealed trait StatelessModelMessage extends ModelMessage

// Basic Model CRUD
case class GetRealtimeModel(domainId: DomainId, modelId: String, session: Option[DomainUserSessionId]) extends StatelessModelMessage

case class CreateOrUpdateRealtimeModel(
                                        domainId: DomainId,
                                        modelId: String,
                                        collectionId: String,
                                        data: ObjectValue,
                                        overridePermissions: Option[Boolean],
                                        worldPermissions: Option[ModelPermissions],
                                        userPermissions: Map[DomainUserId, ModelPermissions],
                                        session: Option[DomainUserSessionId]) extends StatelessModelMessage

case class CreateRealtimeModel(
                                domainId: DomainId,
                                modelId: String,
                                collectionId: String,
                                data: ObjectValue,
                                overridePermissions: Option[Boolean],
                                worldPermissions: Option[ModelPermissions],
                                userPermissions: Map[DomainUserId, ModelPermissions],
                                session: Option[DomainUserSessionId]) extends StatelessModelMessage

case class DeleteRealtimeModel(domainId: DomainId, modelId: String, session: Option[DomainUserSessionId]) extends StatelessModelMessage

// Incoming Permissions Messages
case class GetModelPermissionsRequest(domainId: DomainId, modelId: String, session: DomainUserSessionId) extends StatelessModelMessage

case class SetModelPermissionsRequest(
                                       domainId: DomainId,
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

case class OpenRealtimeModelRequest(domainId: DomainId,
                                    modelId: String,
                                    autoCreateId: Option[Int],
                                    session: DomainUserSessionId,
                                    clientActor: ActorRef) extends RealTimeModelMessage

case class ModelReconnectRequest(domainId: DomainId, modelId: String, session: DomainUserSessionId, contextVersion: Long, clientActor: ActorRef) extends RealTimeModelMessage

case class CloseRealtimeModelRequest(domainId: DomainId, modelId: String, session: DomainUserSessionId) extends RealTimeModelMessage

case class OperationSubmission(domainId: DomainId, modelId: String, seqNo: Int, contextVersion: Long, operation: Operation) extends RealTimeModelMessage

sealed trait ModelReferenceEvent extends RealTimeModelMessage {
  val id: Option[String]
}

case class ShareReference(domainId: DomainId, modelId: String, id: Option[String], key: String, referenceType: ReferenceType.Value, values: List[Any], contextVersion: Long) extends ModelReferenceEvent

case class SetReference(domainId: DomainId, modelId: String, id: Option[String], key: String, referenceType: ReferenceType.Value, values: List[Any], contextVersion: Long) extends ModelReferenceEvent

case class ClearReference(domainId: DomainId, modelId: String, id: Option[String], key: String) extends ModelReferenceEvent

case class UnshareReference(domainId: DomainId, modelId: String, id: Option[String], key: String) extends ModelReferenceEvent

sealed trait InternalRealTimeModelMessage

//
// Outgoing Messages to the client actor
//  


case class OpenModelSuccess(valuePrefix: Long,
                            metaData: OpenModelMetaData,
                            connectedClients: Set[DomainUserSessionId],
                            referencesBySession: Set[ReferenceState],
                            modelData: ObjectValue,
                            modelPermissions: ModelPermissions)

case class ModelReconnectResponse(currentVersion: Long)

case class ModelReconnectComplete(modelId: String,
                                  connectedClients: Set[DomainUserSessionId],
                                  references: Set[ReferenceState],
                                  permissions: ModelPermissions) extends RealtimeModelClientMessage

case class GetModelPermissionsResponse(overridesCollection: Boolean, worldPermissions: ModelPermissions, userPermissions: Map[DomainUserId, ModelPermissions])

trait RealtimeModelClientMessage {
  val modelId: String
}

case class OperationAcknowledgement(modelId: String, seqNo: Int, contextVersion: Long, timestamp: Instant) extends RealtimeModelClientMessage

case class OutgoingOperation(modelId: String,
                             session: DomainUserSessionId,
                             contextVersion: Long,
                             timestamp: Instant,
                             operation: Operation) extends RealtimeModelClientMessage

case class RemoteClientClosed(modelId: String, session: DomainUserSessionId) extends RealtimeModelClientMessage

case class RemoteClientOpened(modelId: String, session: DomainUserSessionId) extends RealtimeModelClientMessage

case class ModelForceClose(modelId: String, reason: String, reasonCode: Int) extends RealtimeModelClientMessage

case class ModelPermissionsChanged(modelId: String, permissions: ModelPermissions) extends RealtimeModelClientMessage

case class ClientAutoCreateModelConfigRequest(modelId: String, autoConfigId: Int) extends RealtimeModelClientMessage

sealed trait RemoteReferenceEvent extends RealtimeModelClientMessage

case class RemoteReferenceShared(
                                  modelId: String, session: DomainUserSessionId, id: Option[String], key: String,
                                  referenceType: ReferenceType.Value, values: List[Any]) extends RemoteReferenceEvent

case class RemoteReferenceSet(modelId: String, session: DomainUserSessionId, id: Option[String], key: String,
                              referenceType: ReferenceType.Value, value: List[Any]) extends RemoteReferenceEvent

case class RemoteReferenceCleared(modelId: String, session: DomainUserSessionId, id: Option[String], key: String) extends RemoteReferenceEvent

case class RemoteReferenceUnshared(modelId: String, session: DomainUserSessionId, id: Option[String], key: String) extends RemoteReferenceEvent
