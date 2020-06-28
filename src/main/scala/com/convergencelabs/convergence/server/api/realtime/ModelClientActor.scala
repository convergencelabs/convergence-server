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

package com.convergencelabs.convergence.server.api.realtime

import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeoutException

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.util.Timeout
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.proto.model.ModelOfflineSubscriptionChangeRequestMessage.ModelOfflineSubscriptionData
import com.convergencelabs.convergence.proto.model.ModelsQueryResponseMessage.ModelResult
import com.convergencelabs.convergence.proto.model.OfflineModelUpdatedMessage.{ModelUpdateData, OfflineModelInitialData, OfflineModelUpdateData}
import com.convergencelabs.convergence.proto.model.{ReferenceValues, _}
import com.convergencelabs.convergence.proto.{ClientMessage, ModelMessage, NormalMessage, RequestMessage}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.api.realtime.ClientActor.{SendServerMessage, SendServerRequest}
import com.convergencelabs.convergence.server.api.realtime.ImplicitMessageConversions.{instanceToTimestamp, messageToObjectValue, modelPermissionsToMessage, modelUserPermissionSeqToMap, objectValueToMessage}
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection.ReplyCallback
import com.convergencelabs.convergence.server.api.rest.badRequest
import com.convergencelabs.convergence.server.datastore.domain.{ModelPermissions, ModelStoreActor}
import com.convergencelabs.convergence.server.domain.model.data.ObjectValue
import com.convergencelabs.convergence.server.domain.model.ot.Operation
import com.convergencelabs.convergence.server.domain.model.reference.RangeReference
import com.convergencelabs.convergence.server.domain.model.{ElementReferenceValues, IndexReferenceValues, PropertyReferenceValues, RangeReferenceValues, RealtimeModelActor, _}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId, DomainUserSessionId}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.google.protobuf.struct.Value
import com.google.protobuf.struct.Value.Kind.{StringValue => ProtoString}
import grizzled.slf4j.Logging
import org.json4s.JsonAST.{JInt, JString}
import scalapb.GeneratedMessage

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

/**
 * The [[ModelClientActor]] handles all incoming and outgoing messages
 * that are specific to the Model subsystem.
 *
 * @param context                  The ActorContext for this actor.
 * @param timers                   The Akka timers instance to use for
 *                                 scheduling.
 * @param domainId                 The domain this client has connected to.
 * @param session                  The unique session for this client.
 * @param clientActor              The client actor managing the web socket
 *                                 communication.
 * @param modelStoreActor          The model store actor store for this domain.
 * @param modelClusterRegion       The model shard region for this domain.
 * @param requestTimeout           The default request timeout.
 * @param offlineModelSyncInterval Specifies how often should offline model
 *                                 synchronization should occur.
 */
class ModelClientActor private(context: ActorContext[ModelClientActor.Message],
                               timers: TimerScheduler[ModelClientActor.Message],
                               domainId: DomainId,
                               session: DomainUserSessionId,
                               clientActor: ActorRef[ClientActor.SendToClient],
                               modelStoreActor: ActorRef[ModelStoreActor.Message],
                               modelClusterRegion: ActorRef[RealtimeModelActor.Message],
                               requestTimeout: Timeout,
                               offlineModelSyncInterval: FiniteDuration)
  extends AbstractBehavior[ModelClientActor.Message](context) with Logging {

  import ModelClientActor._

  private[this] implicit val ec: ExecutionContextExecutor = context.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system
  private[this] implicit val defaultRequestTimeout: Timeout = requestTimeout

  private[this] var nextResourceId = 0
  private[this] var resourceIdToModelId = Map[String, String]()
  private[this] var modelIdToResourceId = Map[String, String]()
  private[this] var subscribedModels = Map[String, OfflineModelState]()
  private[this] var openingModelStash = Map[String, ListBuffer[OutgoingMessage]]()

  timers.startTimerAtFixedRate(SyncTaskTimer, SyncOfflineModels, offlineModelSyncInterval)

  override def onMessage(msg: ModelClientActor.Message): Behavior[ModelClientActor.Message] = {
    msg match {
      case IncomingProtocolMessage(message) =>
        onMessageReceived(message)
      case IncomingProtocolRequest(message, replyPromise) =>
        onRequestReceived(message.asInstanceOf[RequestMessage with ModelMessage], replyPromise)
      case message: OutgoingMessage =>
        onOutgoingModelMessage(message)
      case SyncOfflineModels =>
        syncOfflineModels(this.subscribedModels)
      case message: UpdateOfflineModel =>
        handleOfflineModelSynced(message)
      case message: ModelClosed =>
        onModelClosed(message)
      case message: ModelOpenSuccess =>
        onModelOpenSuccess(message)
      case message: ModelOpenFailure =>
        onModelOpenFailure(message)
      case message: ModelResyncRequestSuccess =>
        onModelResyncSuccess(message)
      case message: ModelResyncRequestFailure =>
        onModelResyncFailure(message)
    }

    Behaviors.same
  }

  override def onSignal: PartialFunction[Signal, Behavior[ModelClientActor.Message]] = super.onSignal orElse {
    case PostStop =>
      timers.cancelAll()
      Behaviors.same
  }

  private[this] def handleOfflineModelSynced(message: UpdateOfflineModel): Unit = {
    val UpdateOfflineModel(modelId, action) = message
    action match {
      case ModelStoreActor.OfflineModelInitial(model, permissions, valueIdPrefix) =>
        this.subscribedModels.get(modelId).foreach { _ =>
          val modelDataUpdate = ModelUpdateData(
            model.metaData.version,
            Some(model.metaData.createdTime),
            Some(model.metaData.modifiedTime),
            Some(model.data)
          )

          val permissionsData = ModelPermissionsData(
            permissions.read,
            permissions.write,
            permissions.remove,
            permissions.manage)

          val prefix = java.lang.Long.toString(valueIdPrefix, 36)
          val initialData = OfflineModelInitialData(model.metaData.collection, prefix, Some(modelDataUpdate), Some(permissionsData))
          val action = OfflineModelUpdatedMessage.Action.Initial(initialData)
          val message = OfflineModelUpdatedMessage(modelId, action)

          clientActor ! SendServerMessage(message)

          val version = model.metaData.version
          this.subscribedModels += modelId -> OfflineModelState(version, permissions)
        }
      case ModelStoreActor.OfflineModelUpdated(model, permissions) =>
        this.subscribedModels.get(modelId).foreach { currentState =>
          val modelUpdate = model.map { m =>
            ModelUpdateData(
              m.metaData.version,
              Some(m.metaData.createdTime),
              Some(m.metaData.modifiedTime),
              Some(m.data)
            )
          }

          val permissionsUpdate = permissions.map { p =>
            ModelPermissionsData(p.read, p.write, p.remove, p.manage)
          }

          val updateData = OfflineModelUpdateData(modelUpdate, permissionsUpdate)
          val action = OfflineModelUpdatedMessage.Action.Updated(updateData)
          val message = OfflineModelUpdatedMessage(modelId, action)
          clientActor ! SendServerMessage(message)

          val version = model.map(_.metaData.version).getOrElse(currentState.currentVersion)
          val perms = permissions.getOrElse(currentState.currentPermissions)
          this.subscribedModels += modelId -> OfflineModelState(version, perms)

        }

      case ModelStoreActor.OfflineModelDeleted() =>
        val message = OfflineModelUpdatedMessage(modelId, OfflineModelUpdatedMessage.Action.Deleted(true))
        clientActor ! SendServerMessage(message)

        this.subscribedModels -= modelId

      case ModelStoreActor.OfflineModelPermissionRevoked() =>
        val message = OfflineModelUpdatedMessage(modelId, OfflineModelUpdatedMessage.Action.PermissionRevoked(true))
        clientActor ! SendServerMessage(message)

        this.subscribedModels -= modelId
      case ModelStoreActor.OfflineModelNotUpdate() =>
      // No update required
    }
  }

  private[this] def syncOfflineModels(models: Map[String, OfflineModelState]): Unit = {
    // FIXME handle the error conditions here better.
    val notOpen = models.filter { case (modelId, _) => !this.modelIdToResourceId.contains(modelId) }
    notOpen.foreach { case (modelId, OfflineModelState(version, permissions)) =>
      modelStoreActor.ask[ModelStoreActor.GetModelUpdateResponse](
        ModelStoreActor.GetModelUpdateRequest(modelId, version, permissions, this.session.userId, _))
        .map(_.result.fold(
          {
            case ModelStoreActor.UnknownError() =>
              error("Error updating offline model")
          },
          {
            action => context.self ! UpdateOfflineModel(modelId, action)
          }))
        .recover(cause => error("Error updating offline model", cause))
    }
  }

  //
  // Outgoing Messages
  //
  private[this] def onOutgoingModelMessage(message: OutgoingMessage): Unit = {
    if (openingModelStash.contains(message.modelId)) {
      message match {

        case autoCreateRequest: ClientAutoCreateModelConfigRequest =>
          // This message is part of the opening process and should go
          // out immediately. All others should be stashed.
          onAutoCreateModelConfigRequest(autoCreateRequest)
        case _ =>
          val stashedMessages = openingModelStash(message.modelId)
          stashedMessages += message
      }
    } else {
      message match {
        case op: OutgoingOperation =>
          onOutgoingOperation(op)
        case opAck: OperationAcknowledgement =>
          onOperationAcknowledgement(opAck)
        case remoteOpened: RemoteClientOpened =>
          onRemoteClientOpened(remoteOpened)
        case remoteClosed: RemoteClientClosed =>
          onRemoteClientClosed(remoteClosed)
        case forceClosed: ModelForceClose =>
          onModelForceClose(forceClosed)
        case autoCreateRequest: ClientAutoCreateModelConfigRequest =>
        // FIXME we are already opened.. this should not happen???
        case refShared: RemoteReferenceShared =>
          onRemoteReferenceShared(refShared)
        case refUnshared: RemoteReferenceUnshared =>
          onRemoteReferenceUnshared(refUnshared)
        case refSet: RemoteReferenceSet =>
          onRemoteReferenceSet(refSet)
        case refCleared: RemoteReferenceCleared =>
          onRemoteReferenceCleared(refCleared)
        case permsChanged: ModelPermissionsChanged =>
          onModelPermissionsChanged(permsChanged)
        case message: ModelResyncServerComplete =>
          onModelResyncServerComplete(message)
        case resyncStarted: RemoteClientResyncStarted =>
          onRemoteClientResyncStarted(resyncStarted)
        case resyncCompleted: RemoteClientResyncCompleted =>
          onRemoteClientResyncCompleted(resyncCompleted)
        case ServerError(_, ExpectedError(code, message, details)) =>
          val errorMessage = ErrorMessage(code, message, JsonProtoConverter.jValueMapToValueMap(details))
          clientActor ! SendServerMessage(errorMessage)
      }
    }
  }

  private[this] def onOutgoingOperation(op: OutgoingOperation): Unit = {
    val OutgoingOperation(modelId, session, contextVersion, timestamp, operation) = op
    resourceId(modelId) foreach { resourceId =>
      val message = RemoteOperationMessage(
        resourceId,
        session.sessionId,
        contextVersion,
        Some(timestamp),
        Some(OperationMapper.mapOutgoing(operation)))

      clientActor ! SendServerMessage(message)

      this.subscribedModels.get(modelId).foreach(state => {
        val newState = state.copy(currentVersion = contextVersion)
        this.subscribedModels += (modelId -> newState)
      })
    }
  }

  private[this] def onOperationAcknowledgement(opAck: OperationAcknowledgement): Unit = {
    val OperationAcknowledgement(modelId, seqNo, version, timestamp) = opAck
    resourceId(modelId) foreach { resourceId =>
      val message = OperationAcknowledgementMessage(resourceId, seqNo, version, Some(timestamp))
      clientActor ! SendServerMessage(message)
    }
  }

  private[this] def onRemoteClientOpened(opened: RemoteClientOpened): Unit = {
    val RemoteClientOpened(modelId, session) = opened
    resourceId(modelId) foreach { resourceId =>
      val serverMessage = RemoteClientOpenedMessage(resourceId, session.sessionId)
      clientActor ! SendServerMessage(serverMessage)
    }
  }

  private[this] def onRemoteClientClosed(closed: RemoteClientClosed): Unit = {
    val RemoteClientClosed(modelId, session) = closed
    resourceId(modelId) foreach { resourceId =>
      val serverMessage = RemoteClientClosedMessage(resourceId, session.sessionId)
      clientActor ! SendServerMessage(serverMessage)
    }
  }

  private[this] def onModelPermissionsChanged(permsChanged: ModelPermissionsChanged): Unit = {
    val ModelPermissionsChanged(modelId, permissions) = permsChanged
    resourceId(modelId) foreach { resourceId =>
      val serverMessage = ModelPermissionsChangedMessage(resourceId, Some(permissions))
      clientActor ! SendServerMessage(serverMessage)

      this.subscribedModels.get(modelId).foreach(state => {
        val newState = state.copy(currentPermissions = permissions)
        this.subscribedModels += (modelId -> newState)
      })
    }
  }

  private[this] def onModelForceClose(forceClose: ModelForceClose): Unit = {
    val ModelForceClose(modelId, reason, reasonCode) = forceClose
    resourceId(modelId) foreach { resourceId =>
      modelIdToResourceId -= modelId
      resourceIdToModelId -= resourceId
      openingModelStash -= modelId
      val serverMessage = ModelForceCloseMessage(resourceId, reason, reasonCode.id)
      clientActor ! SendServerMessage(serverMessage)
    }
  }

  private[this] def onAutoCreateModelConfigRequest(autoConfigRequest: ClientAutoCreateModelConfigRequest): Unit = {
    val ClientAutoCreateModelConfigRequest(_, autoConfigId, replyTo) = autoConfigRequest
    clientActor.ask[Any](SendServerRequest(AutoCreateModelConfigRequestMessage(autoConfigId), _))
      .mapTo[AutoCreateModelConfigResponseMessage]
      .map {
        case AutoCreateModelConfigResponseMessage(collection, data, overridePermissions, worldPermissionsData, userPermissionsData, ephemeral, _) =>
          val worldPermissions = worldPermissionsData.map {
            case ModelPermissionsData(read, write, remove, manage, _) =>
              ModelPermissions(read, write, remove, manage)
          }

          val userPermissions = modelUserPermissionSeqToMap(userPermissionsData)
          val config = ClientAutoCreateModelConfig(
            collection,
            data.map(messageToObjectValue),
            Some(overridePermissions),
            worldPermissions,
            userPermissions,
            Some(ephemeral))
          ClientAutoCreateModelConfigResponse(Right(config))
      }
      .recover {
        case _: TimeoutException =>
          ClientAutoCreateModelConfigResponse(Left(ClientAutoCreateModelConfigTimeout()))
        case cause: ClassCastException =>
          warn("Client returned invalid data for model auto create config", cause)
          ClientAutoCreateModelConfigResponse(Left(ClientAutoCreateModelConfigInvalid()))
        case cause: Throwable =>
          error("Unexpected error getting auto create config from client", cause)
          ClientAutoCreateModelConfigResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onModelResyncServerComplete(message: ModelResyncServerComplete): Unit = {
    val ModelResyncServerComplete(modelId, connectedClients, resyncingClients, references) = message
    resourceId(modelId) foreach { resourceId =>
      val convertedReferences = convertReferences(references)
      val serverMessage = ModelResyncServerCompleteMessage(
        resourceId,
        connectedClients.map(s => s.sessionId).toSeq,
        resyncingClients.map(s => s.sessionId).toSeq,
        convertedReferences)
      clientActor ! SendServerMessage(serverMessage)
    }
  }

  private[this] def onRemoteReferenceShared(refShared: RemoteReferenceShared): Unit = {
    val RemoteReferenceShared(modelId, session, valueId, key, values) = refShared
    resourceId(modelId) foreach { resourceId =>
      val references = mapOutgoingReferenceValue(values)
      val serverMessage = RemoteReferenceSharedMessage(resourceId, valueId, key, Some(references), session.sessionId)
      clientActor ! SendServerMessage(serverMessage)
    }
  }

  private[this] def onRemoteReferenceUnshared(refUnshared: RemoteReferenceUnshared): Unit = {
    val RemoteReferenceUnshared(modelId, session, valueId, key) = refUnshared
    resourceId(modelId) foreach { resourceId =>
      val serverMessage = RemoteReferenceUnsharedMessage(resourceId, valueId, key, session.sessionId)
      clientActor ! SendServerMessage(serverMessage)
    }
  }

  private[this] def onRemoteReferenceSet(refSet: RemoteReferenceSet): Unit = {
    val RemoteReferenceSet(modelId, session, valueId, key, values) = refSet
    resourceId(modelId) foreach { resourceId =>
      val references = mapOutgoingReferenceValue(values)
      val serverMessage = RemoteReferenceSetMessage(resourceId, valueId, key, Some(references), session.sessionId)
      clientActor ! SendServerMessage(serverMessage)
    }
  }

  private[this] def onRemoteClientResyncStarted(message: RemoteClientResyncStarted): Unit = {
    val RemoteClientResyncStarted(modelId, remoteSession) = message
    resourceId(modelId) foreach { resourceId =>
      val serverMessage = RemoteClientResyncStartedMessage(resourceId, remoteSession.sessionId)
      clientActor ! SendServerMessage(serverMessage)
    }
  }

  private[this] def onRemoteClientResyncCompleted(message: RemoteClientResyncCompleted): Unit = {
    val RemoteClientResyncCompleted(modelId, remoteSession) = message
    resourceId(modelId) foreach { resourceId =>
      val serverMessage = RemoteClientResyncCompletedMessage(resourceId, remoteSession.sessionId)
      clientActor ! SendServerMessage(serverMessage)
    }
  }

  private[this] def mapOutgoingReferenceValue(values: ModelReferenceValues): ReferenceValues = {
    values match {
      case IndexReferenceValues(indices) =>
        ReferenceValues().withIndices(Int32List(indices))
      case RangeReferenceValues(ranges) =>
        val protoRanges = ranges.map {
          case RangeReference.Range(from, to) => IndexRange(from, to)
        }
        ReferenceValues().withRanges(IndexRangeList(protoRanges))
      case PropertyReferenceValues(properties) =>
        ReferenceValues().withProperties(StringList(properties))
      case ElementReferenceValues(elementIds) =>
        ReferenceValues().withElements(StringList(elementIds))
    }
  }

  private[this] def mapIncomingReference(values: ReferenceValues): Either[Unit, ModelReferenceValues] = {
    values.values match {
      case ReferenceValues.Values.Indices(Int32List(indices, _)) =>
        Right(IndexReferenceValues(indices.toList))
      case ReferenceValues.Values.Ranges(IndexRangeList(ranges, _)) =>
        val mapped = ranges.map(r => RangeReference.Range(r.startIndex, r.endIndex)).toList
        Right(RangeReferenceValues(mapped))
      case ReferenceValues.Values.Properties(StringList(properties, _)) =>
        Right(PropertyReferenceValues(properties.toList))
      case ReferenceValues.Values.Elements(StringList(elements, _)) =>
        Right(ElementReferenceValues(elements.toList))
      case ReferenceValues.Values.Empty =>
        Left(())
    }
  }

  private[this] def onRemoteReferenceCleared(refCleared: RemoteReferenceCleared): Unit = {
    val RemoteReferenceCleared(modelId, session, valueId, key) = refCleared
    resourceId(modelId) foreach { resourceId =>
      val serverMessage = RemoteReferenceClearedMessage(resourceId, valueId, key, session.sessionId)
      clientActor ! SendServerMessage(serverMessage)
    }
  }

  //
  // Incoming Messages
  //

  private[this] def onRequestReceived(message: RequestMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case openRequest: OpenRealtimeModelRequestMessage =>
        onOpenRealtimeModelRequest(openRequest, replyCallback)
      case resyncRequest: ModelResyncRequestMessage =>
        onModelResyncRequest(resyncRequest, replyCallback)
      case closeRequest: CloseRealtimeModelRequestMessage =>
        onCloseRealtimeModelRequest(closeRequest, replyCallback)
      case createRequest: CreateRealtimeModelRequestMessage =>
        onCreateRealtimeModelRequest(createRequest, replyCallback)
      case deleteRequest: DeleteRealtimeModelRequestMessage =>
        onDeleteRealtimeModelRequest(deleteRequest, replyCallback)
      case queryRequest: ModelsQueryRequestMessage =>
        onModelQueryRequest(queryRequest, replyCallback)
      case getPermissionRequest: GetModelPermissionsRequestMessage =>
        onGetModelPermissionsRequest(getPermissionRequest, replyCallback)
      case setPermissionRequest: SetModelPermissionsRequestMessage =>
        onSetModelPermissionsRequest(setPermissionRequest, replyCallback)
      case message: ModelOfflineSubscriptionChangeRequestMessage =>
        onModelOfflineSubscription(message, replyCallback)
    }
  }

  private[this] def onMessageReceived(message: NormalMessage with ModelMessage): Unit = {
    message match {
      case message: OperationSubmissionMessage =>
        onOperationSubmission(message)
      case message: ShareReferenceMessage =>
        onShareReference(message)
      case message: UnshareReferenceMessage =>
        onUnshareReference(message)
      case message: SetReferenceMessage =>
        onSetReference(message)
      case message: ClearReferenceMessage =>
        onClearReference(message)
      case message: ModelResyncClientCompleteMessage =>
        onModelResyncClientComplete(message)
    }
  }

  private[this] def onOperationSubmission(message: OperationSubmissionMessage): Unit = {
    val OperationSubmissionMessage(resourceId, seqNo, version, operation, _) = message
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        operation match {
          case Some(op) =>
            OperationMapper.mapIncoming(op).fold({ _ =>
              warn(s"$domainId: Received an operation submissions with an invalid operation: $message")
              invalidOperation(message)
            },
              { mappedOp =>
                val submission = RealtimeModelActor.OperationSubmission(domainId, modelId, session, seqNo, version, mappedOp)
                modelClusterRegion ! submission
              })
          case None =>
            warn(s"$domainId: Received an operation submissions with an empty operation: $message")
            invalidOperation(message)
        }

      case None =>
        warn(s"$domainId: Received an operation submissions for a resource id that does not exists.")
        val serverMessage = unknownResourceId(resourceId)
        clientActor ! SendServerMessage(serverMessage)
    }
  }

  private[this] def onModelResyncClientComplete(message: ModelResyncClientCompleteMessage): Unit = {
    val ModelResyncClientCompleteMessage(resourceId, open, _) = message
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val message = RealtimeModelActor.ModelResyncClientComplete(domainId, modelId, session, open)
        modelClusterRegion ! message
      case None =>
        warn(s"$domainId: Received model resync client complete message for an unknown resource id.")
        val serverMessage = unknownResourceId(resourceId)
        clientActor ! SendServerMessage(serverMessage)
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // References
  /////////////////////////////////////////////////////////////////////////////

  private[this] def onShareReference(message: ShareReferenceMessage): Unit = {
    val ShareReferenceMessage(resourceId, valueId, key, references, version, _) = message
    val vId = valueId.filter(!_.isEmpty)

    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        references match {
          case Some(refs) =>
            mapIncomingReference(refs).map { values =>
              val publishReference = RealtimeModelActor.ShareReference(domainId, modelId, session, vId, key, values, version)
              modelClusterRegion ! publishReference
            }.left.map { _ =>
              invalidReferenceType(message.toString)
            }
          case None =>
            noReferenceValues(message.toString)
        }
      case None =>
        noResourceIdForReferenceMessage(resourceId, message.toString)
    }
  }

  def onUnshareReference(message: UnshareReferenceMessage): Unit = {
    val UnshareReferenceMessage(resourceId, valueId, key, _) = message
    val vId = valueId.filter(!_.isEmpty)
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val unshareReference = RealtimeModelActor.UnShareReference(domainId, modelId, session, vId, key)
        modelClusterRegion ! unshareReference
      case None =>
        noResourceIdForReferenceMessage(resourceId, message.toString)
    }
  }

  private[this] def onSetReference(message: SetReferenceMessage): Unit = {
    val SetReferenceMessage(resourceId, valueId, key, references, version, _) = message
    val vId = valueId.filter(!_.isEmpty)

    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        references match {
          case Some(refs) =>
            mapIncomingReference(refs).map { values =>
              val setReference = RealtimeModelActor.SetReference(domainId, modelId, session, vId, key, values, version)
              modelClusterRegion ! setReference
            }.left.map { _ =>
              invalidReferenceType(message.toString)
            }
          case None =>
            noReferenceValues(message.toString)
        }
      case None =>
        noResourceIdForReferenceMessage(resourceId, message.toString)
    }
  }

  private[this] def onClearReference(message: ClearReferenceMessage): Unit = {
    val ClearReferenceMessage(resourceId, valueId, key, _) = message
    val vId = valueId.filter(!_.isEmpty)
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val clearReference = RealtimeModelActor.ClearReference(domainId, modelId, session, vId, key)
        modelClusterRegion ! clearReference
      case None =>
        warn(s"$domainId: Received a reference clear message for a resource id that does not exists.")
        val serverMessage = unknownResourceId(resourceId)
        clientActor ! SendServerMessage(serverMessage)
    }
  }

  private[this] def convertReferences(references: Set[ReferenceState]): Seq[ReferenceData] = {
    references.map {
      case ReferenceState(sessionId, valueId, key, values) =>
        val referenceValues = mapOutgoingReferenceValue(values)
        ReferenceData(sessionId.sessionId, valueId, key, Some(referenceValues))
    }.toSeq
  }

  private[this] def noResourceIdForReferenceMessage(resourceId: String, message: String): () = {
    warn(s"$domainId: Received a reference message for a resource id that does not exists $message")
    val serverMessage = unknownResourceId(resourceId)
    clientActor ! SendServerMessage(serverMessage)
  }

  private[this] def noReferenceValues(message: String): () = {
    warn(s"$domainId: Received a reference set with no reference values.")
    val errorMessage = ErrorMessage(
      ErrorCodes.InvalidMessage.toString,
      s"Invalid reference set message. No reference values were specified: $message")
    clientActor ! SendServerMessage(errorMessage)
  }

  private[this] def invalidReferenceType(message: String): () = {
    warn(s"$domainId: Received a reference set with an invalid reference type.")
    val errorMessage = ErrorMessage(
      ErrorCodes.InvalidMessage.toString,
      "Invalid reference set message. Invalid reference type: " + message)
    clientActor ! SendServerMessage(errorMessage)
  }

  private[this] def onModelOfflineSubscription(message: ModelOfflineSubscriptionChangeRequestMessage, replyCallback: ReplyCallback): Unit = {
    val ModelOfflineSubscriptionChangeRequestMessage(subscribe, unsubscribe, all, _) = message

    val previousModels = this.subscribedModels.keySet

    if (all) {
      this.subscribedModels = Map()
    }

    unsubscribe.foreach(modelId => this.subscribedModels -= modelId)

    subscribe.foreach { case ModelOfflineSubscriptionData(modelId, version, permissions, _) =>
      val ModelPermissionsData(read, write, remove, manage, _) = permissions.getOrElse(NoPermissions)
      val state = OfflineModelState(version, ModelPermissions(read, write, remove, manage))
      this.subscribedModels += modelId -> state
    }

    val newModels = this.subscribedModels.filter {
      case (modelId, _) => !previousModels.contains(modelId)
    }

    this.syncOfflineModels(newModels)

    replyCallback.reply(OkResponse())
  }

  private[this] def onCloseRealtimeModelRequest(request: CloseRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val CloseRealtimeModelRequestMessage(resourceId, _) = request
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        modelClusterRegion
          .ask[RealtimeModelActor.CloseRealtimeModelResponse](
            RealtimeModelActor.CloseRealtimeModelRequest(domainId, modelId, session, _))
          .map(_.response.fold(
            {
              case RealtimeModelActor.ModelNotOpenError() =>
                cb.expectedError(ErrorCodes.ModelNotOpen, s"The model '$modelId' could not be closed because it was not open.'")
              case RealtimeModelActor.UnknownError() =>
                cb.unexpectedError("An unexpected error occurred while closing the model.")
            },
            { _ =>
              context.self ! ModelClosed(modelId, resourceId, cb)
            })
          )
          .recover { cause =>
            warn("A timeout occurred closing a model", cause)
            cb.timeoutError()
          }
      case None =>
        cb.expectedError(ErrorCodes.ModelNotOpen, s"the requested model was not open")
    }
  }

  private[this] def onModelClosed(message: ModelClosed): Unit = {
    val ModelClosed(modelId, resourceId, cb) = message
    resourceIdToModelId -= resourceId
    modelIdToResourceId -= modelId
    cb.reply(CloseRealTimeModelResponseMessage())
  }

  private[this] def onOpenRealtimeModelRequest(request: OpenRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val OpenRealtimeModelRequestMessage(optionalModelId, autoCreateId, _) = request
    val modelId = getSetOrRandomModelId(optionalModelId)

    openingModelStash += (modelId -> ListBuffer())

    val narrowedSelf = context.self.narrow[OutgoingMessage]
    modelClusterRegion.ask[RealtimeModelActor.OpenRealtimeModelResponse](
      RealtimeModelActor.OpenRealtimeModelRequest(domainId, modelId, autoCreateId, session, narrowedSelf, _))
      .map(_.response.fold(
        error => context.self ! ModelOpenFailure(modelId, Right(error), cb),
        message => context.self ! ModelOpenSuccess(modelId, message, cb)
      ))
      .recover(cause => context.self ! ModelOpenFailure(modelId, Left(cause), cb))
  }

  private[this] def onModelOpenFailure(message: ModelOpenFailure): Unit = {
    val ModelOpenFailure(modelId, failure, cb) = message

    // Remove the message stash since we didn't open we aren't going to
    // send the messages on.
    openingModelStash -= modelId

    failure
      .map {
        case RealtimeModelActor.ModelAlreadyOpenError() =>
          ModelClientActor.modelAlreadyOpenError(cb, modelId)
        case RealtimeModelActor.ModelAlreadyOpeningError() =>
          ModelClientActor.modelAlreadyOpeningError(cb, modelId)
        case RealtimeModelActor.ModelClosingAfterErrorError() =>
          modelClosingAfterErrorError(cb, modelId)
        case RealtimeModelActor.ModelDeletedWhileOpeningError() =>
          ModelClientActor.modelDeletedError(cb, modelId)
        case RealtimeModelActor.ClientDataRequestError(message) =>
          cb.expectedError(ErrorCodes.ModelClientDataRequestFailure, message)
        case RealtimeModelActor.ModelNotFoundError() =>
          ModelClientActor.modelNotFoundError(cb, modelId)
        case RealtimeModelActor.UnauthorizedError(message) =>
          cb.reply(ErrorMessages.Unauthorized(message))
        case RealtimeModelActor.UnknownError() =>
          cb.unknownError()
        case RealtimeModelActor.ClientErrorResponse(message) =>
          cb.expectedError(ErrorCodes.ModelClientDataRequestFailure, message)
      }
      .left
      .map { cause =>
        warn("A timeout occurred waiting for an open model request", cause)
        cb.timeoutError()
      }
  }

  private[this] def onModelOpenSuccess(message: ModelOpenSuccess): Unit = {
    val ModelOpenSuccess(modelId, success, cb) = message
    val RealtimeModelActor.OpenModelSuccess(valueIdPrefix, metaData, connectedClients, resyncingClients, references, modelData, modelPermissions) = success

    val resourceId = claimResourceId(modelId)

    val convertedReferences = convertReferences(references)
    cb.reply(
      OpenRealtimeModelResponseMessage(
        resourceId,
        metaData.id,
        metaData.collection,
        java.lang.Long.toString(valueIdPrefix, 36),
        metaData.version,
        Some(metaData.createdTime),
        Some(metaData.modifiedTime),
        Some(modelData),
        connectedClients.map(s => s.sessionId).toSeq,
        resyncingClients.map(s => s.sessionId).toSeq,
        convertedReferences,
        Some(ModelPermissionsData(
          modelPermissions.read,
          modelPermissions.write,
          modelPermissions.remove,
          modelPermissions.manage))))

    flushOpenStash(modelId)
  }

  private[this] def flushOpenStash(modelId: String): Unit = {
    val stashedMessages = openingModelStash.getOrElse(modelId, ListBuffer())

    // Note: We must remove the stash before processing the messages or
    // else they would be re stashed.
    openingModelStash -= modelId

    // replay these messages into the outgoing message handler to get
    // them shipped off to the client.
    stashedMessages.foreach(message => onOutgoingModelMessage(message))
  }

  private[this] def onModelResyncRequest(request: ModelResyncRequestMessage, cb: ReplyCallback): Unit = {
    val ModelResyncRequestMessage(modelId, contextVersion, _) = request
    val narrowedSelf = context.self.narrow[OutgoingMessage]

    openingModelStash += (modelId -> ListBuffer())

    modelClusterRegion.ask[RealtimeModelActor.ModelResyncResponse](
      RealtimeModelActor.ModelResyncRequest(domainId, modelId, session, contextVersion, narrowedSelf, _))
      .map(_.response.fold(
        error => context.self ! ModelResyncRequestFailure(modelId, Right(error), cb),
        data => context.self ! ModelResyncRequestSuccess(modelId, data, cb)
      ))
      .recover(cause => context.self ! ModelResyncRequestFailure(modelId, Left(cause), cb))
  }

  private[this] def onModelResyncSuccess(message: ModelResyncRequestSuccess): Unit = {
    val ModelResyncRequestSuccess(modelId, data, cb) = message
    val RealtimeModelActor.ModelResyncResponseData(currentVersion, permissions) = data

    val ModelPermissions(read, write, remove, manage) = permissions
    val permissionData = ModelPermissionsData(read, write, remove, manage)
    val resourceId = claimResourceId(modelId)
    val responseMessage = ModelResyncResponseMessage(resourceId, currentVersion, Some(permissionData))
    cb.reply(responseMessage)

    flushOpenStash(modelId)
  }

  private[this] def claimResourceId(modelId: String): String = {
    val resourceId = generateNextResourceId()
    debug(s"Mapping model id '$modelId' to resource id '$resourceId'")

    resourceIdToModelId += (resourceId -> modelId)
    modelIdToResourceId += (modelId -> resourceId)

    resourceId
  }

  private[this] def onModelResyncFailure(message: ModelResyncRequestFailure): Unit = {
    val ModelResyncRequestFailure(modelId, failure, cb) = message

    openingModelStash -= modelId

    failure
      .map {
        case RealtimeModelActor.ModelAlreadyOpenError() =>
          ModelClientActor.modelAlreadyOpenError(cb, modelId)
        case RealtimeModelActor.ModelAlreadyOpeningError() =>
          ModelClientActor.modelAlreadyOpeningError(cb, modelId)
        case RealtimeModelActor.ModelClosingAfterErrorError() =>
          modelClosingAfterErrorError(cb, modelId)
        case RealtimeModelActor.ModelNotFoundError() =>
          ModelClientActor.modelNotFoundError(cb, modelId)
        case RealtimeModelActor.UnauthorizedError(message) =>
          cb.reply(ErrorMessages.Unauthorized(message))
        case RealtimeModelActor.UnknownError() =>
          cb.unknownError()
      }
      .left
      .map { cause =>
        warn("A timeout occurred waiting for an model resync request", cause)
        cb.timeoutError()
      }
  }

  private[this] def onCreateRealtimeModelRequest(request: CreateRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val CreateRealtimeModelRequestMessage(collectionId, optionalModelId, data, overridePermissions, worldPermissionsData, userPermissionsData, _) = request
    val worldPermissions = worldPermissionsData.map(w =>
      ModelPermissions(w.read, w.write, w.remove, w.manage))

    val userPermissions = modelUserPermissionSeqToMap(userPermissionsData)

    val modelId = getSetOrRandomModelId(optionalModelId)

    modelClusterRegion
      .ask[RealtimeModelActor.CreateRealtimeModelResponse](
        RealtimeModelActor.CreateRealtimeModelRequest(
          domainId,
          modelId,
          collectionId,
          data.get,
          Some(overridePermissions),
          worldPermissions,
          userPermissions,
          Some(session),
          _))
      .map(_.response.fold(
        {
          case RealtimeModelActor.ModelAlreadyExistsError() =>
            cb.expectedError(ErrorCodes.ModelAlreadyExists, s"A model with the id '$modelId' already exists")
          case RealtimeModelActor.UnauthorizedError(message) =>
            cb.reply(ErrorMessages.Unauthorized(message))
          case RealtimeModelActor.InvalidCreationDataError(message) =>
            badRequest(message)
          case RealtimeModelActor.UnknownError() =>
            cb.unexpectedError("could not create model")
        },
        modelId => cb.reply(CreateRealtimeModelResponseMessage(modelId))
      ))
      .recover { cause =>
        warn("A timeout occurred waiting for an close model request", cause)
        cb.timeoutError()
      }
  }

  private[this] def onDeleteRealtimeModelRequest(request: DeleteRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val DeleteRealtimeModelRequestMessage(modelId, _) = request
    // We may or may not be able to delete the model, but the user has obviously unsubscribed.
    this.subscribedModels -= request.modelId
    modelClusterRegion
      .ask[RealtimeModelActor.DeleteRealtimeModelResponse](
        RealtimeModelActor.DeleteRealtimeModelRequest(domainId, modelId, Some(session), _))
      .map(_.response.fold(
        {
          case RealtimeModelActor.ModelNotFoundError() =>
            ModelClientActor.modelNotFoundError(cb, modelId)
          case RealtimeModelActor.UnauthorizedError(message) =>
            cb.reply(ErrorMessages.Unauthorized(message))
          case RealtimeModelActor.UnknownError() =>
            cb.unexpectedError(s"An unexpected error occurred while attempting to delete model '$modelId'")
        },
        { _ =>
          cb.reply(DeleteRealtimeModelResponseMessage())
        }
      ))
      .recover { cause =>
        warn("A timeout occurred waiting for a delete model request", cause)
        cb.timeoutError()
      }
  }

  private[this] def onModelQueryRequest(request: ModelsQueryRequestMessage, cb: ReplyCallback): Unit = {
    val ModelsQueryRequestMessage(query, _) = request
    modelStoreActor.ask[ModelStoreActor.QueryModelsResponse](
      ModelStoreActor.QueryModelsRequest(session.userId, query, _))
      .map(_.result.fold(
        {
          case ModelStoreActor.InvalidQueryError(message, _, index) =>
            val details = index.map(i => Map("index" -> JInt(i))).getOrElse(Map())
            cb.expectedError(ErrorCodes.ModelInvalidQuery, message, details)
          case ModelStoreActor.UnknownError() =>
            cb.unexpectedError("Unexpected error querying models.")
        },
        { result =>
          val models = result.data.map {
            r =>
              ModelResult(
                r.metaData.collection,
                r.metaData.id,
                Some(r.metaData.createdTime),
                Some(r.metaData.modifiedTime),
                r.metaData.version,
                Some(JsonProtoConverter.toStruct(r.data)))
          }
          cb.reply(ModelsQueryResponseMessage(models, result.offset, result.count))
        }
      ))
      .recover { cause =>
        warn("A timeout occurred waiting for a model query request", cause)
        cb.timeoutError()
      }
  }

  private[this] def onGetModelPermissionsRequest(request: GetModelPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetModelPermissionsRequestMessage(modelId, _) = request
    modelClusterRegion
      .ask[RealtimeModelActor.GetModelPermissionsResponse](
        RealtimeModelActor.GetModelPermissionsRequest(domainId, modelId, session, _))
      .map(_.response.fold(
        {
          case RealtimeModelActor.ModelNotFoundError() =>
            ModelClientActor.modelNotFoundError(cb, modelId)
          case RealtimeModelActor.UnauthorizedError(message) =>
            cb.reply(ErrorMessages.Unauthorized(message))
          case RealtimeModelActor.UnknownError() =>
            cb.unexpectedError("could get model permissions")
        },
        {
          case RealtimeModelActor.GetModelPermissionsSuccess(overridesCollection, world, users) =>
            val mappedWorld = ModelPermissionsData(world.read, world.write, world.remove, world.manage)
            val mappedUsers = modelUserPermissionSeqToMap(users)
            cb.reply(GetModelPermissionsResponseMessage(overridesCollection, Some(mappedWorld), mappedUsers))
        }
      ))
      .recover { cause =>
        warn("A timeout occurred getting model permissions", cause)
        cb.timeoutError()
      }
  }

  private[this] def onSetModelPermissionsRequest(request: SetModelPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val SetModelPermissionsRequestMessage(modelId, overridePermissions, world, setAllUsers, addedUsers, removedUsers, _) = request
    val mappedWorld = world map (w => ModelPermissions(w.read, w.write, w.remove, w.manage))
    val mappedAddedUsers = modelUserPermissionSeqToMap(addedUsers)

    modelClusterRegion
      .ask[RealtimeModelActor.SetModelPermissionsResponse](
        RealtimeModelActor.SetModelPermissionsRequest(
          domainId,
          modelId,
          session,
          overridePermissions,
          mappedWorld,
          setAllUsers,
          mappedAddedUsers,
          removedUsers.map(ImplicitMessageConversions.dataToDomainUserId).toList,
          _))
      .map(_.response.fold(
        {
          case RealtimeModelActor.ModelNotFoundError() =>
            ModelClientActor.modelNotFoundError(cb, modelId)
          case RealtimeModelActor.UnauthorizedError(message) =>
            cb.reply(ErrorMessages.Unauthorized(message))
          case RealtimeModelActor.UnknownError() =>
            cb.unexpectedError("could set model permissions")
        },
        { _ =>
          cb.reply(SetModelPermissionsResponseMessage())
        }
      ))
      .recover { cause =>
        warn("A timeout occurred setting model permissions", cause)
        cb.timeoutError()
      }
  }

  private[this] def resourceId(modelId: String): Option[String] = {
    this.modelIdToResourceId.get(modelId) orElse {
      error(s"$domainId: Receive an outgoing message for a modelId that is not open: $modelId")
      None
    }
  }

  private[this] def generateNextResourceId(): String = {
    val id = nextResourceId.toString
    nextResourceId += 1
    id
  }

  private[this] def getSetOrRandomModelId(optionalModelId: Option[String]): String = {
    optionalModelId.filter(!_.isEmpty).getOrElse(UUID.randomUUID().toString)
  }
}

object ModelClientActor {
  private[realtime] def apply(domain: DomainId,
                              session: DomainUserSessionId,
                              clientActor: ActorRef[ClientActor.SendToClient],
                              modelStoreActor: ActorRef[ModelStoreActor.Message],
                              modelShardRegion: ActorRef[RealtimeModelActor.Message],
                              requestTimeout: Timeout,
                              offlineModelSyncInterval: FiniteDuration): Behavior[Message] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new ModelClientActor(context, timers, domain, session, clientActor, modelStoreActor, modelShardRegion, requestTimeout, offlineModelSyncInterval)
      }
    }

  private val NoPermissions = ModelPermissionsData(read = false, write = false, remove = false, manage = false)

  private final case object SyncTaskTimer

  private def modelNotFoundError(cb: ReplyCallback, id: String): Unit = {
    val details = Map("id" -> JString(id))
    val message = s"A model with id '$id' does not exist."
    cb.expectedError(ErrorCodes.ModelNotFound, message, details)
  }

  private def invalidOperation(operation: OperationSubmissionMessage): ErrorMessage = {
    val opString = operation.operation.map(_.toString).getOrElse("no operation supplied")
    ErrorMessage(
      ErrorCodes.ModelInvalidOperation.toString,
      s"An operation submission contained an invalid operation.",
      Map("operation" -> Value(ProtoString(opString))))
  }

  private def unknownResourceId(resourceId: String) = ErrorMessage(
    ErrorCodes.ModelUnknownResourceId.toString,
    s"A model with resource id '$resourceId' does not exist.",
    Map("resourceId" -> Value(ProtoString(resourceId))))

  private def modelAlreadyOpenError(cb: ReplyCallback, id: String): Unit = {
    val details = Map("id" -> JString(id))
    val message = s"The model with id '$id' is already open."
    cb.expectedError(ErrorCodes.ModelAlreadyOpen, message, details)
  }

  private def modelAlreadyOpeningError(cb: ReplyCallback, id: String): Unit = {
    val details = Map("id" -> JString(id))
    val message = s"The model with id '$id' is already being opened by this client."
    cb.expectedError(ErrorCodes.ModelAlreadyOpening, message, details)
  }

  private def modelClosingAfterErrorError(cb: ReplyCallback, id: String): Unit = {
    val details = Map("id" -> JString(id))
    val message = s"The model is currently shutting down after an error. You may be able to reopen the model."
    cb.expectedError(ErrorCodes.ModelAlreadyOpening, message, details)
  }

  private def modelDeletedError(cb: ReplyCallback, id: String): Unit = {
    val details = Map("id" -> JString(id))
    val message = s"The model with id '$id' was deleted."
    cb.expectedError(ErrorCodes.ModelDeleted, message, details)
  }

  private final case object SyncOfflineModels extends Message

  private final case class OfflineModelState(currentVersion: Long, currentPermissions: ModelPermissions)

  private final case class UpdateOfflineModel(modelId: String, action: ModelStoreActor.ModelUpdateResult) extends Message


  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  //
  // Messages from the client
  //
  private[realtime] sealed trait IncomingMessage extends Message

  private[realtime] type IncomingNormalMessage = GeneratedMessage with NormalMessage with ModelMessage with ClientMessage

  private[realtime] final case class IncomingProtocolMessage(message: IncomingNormalMessage) extends IncomingMessage

  private[realtime] type IncomingRequestMessage = GeneratedMessage with RequestMessage with ModelMessage with ClientMessage

  private[realtime] final case class IncomingProtocolRequest(message: IncomingRequestMessage, replyCallback: ReplyCallback) extends IncomingMessage


  //
  // Messages from the server
  //
  trait OutgoingMessage extends Message {
    val modelId: String
  }


  final case class ServerError(modelId: String, expectedError: ExpectedError) extends OutgoingMessage

  final case class OperationAcknowledgement(modelId: String, seqNo: Int, contextVersion: Long, timestamp: Instant) extends OutgoingMessage

  final case class OutgoingOperation(modelId: String,
                                     session: DomainUserSessionId,
                                     contextVersion: Long,
                                     timestamp: Instant,
                                     operation: Operation) extends OutgoingMessage

  final case class RemoteClientClosed(modelId: String, session: DomainUserSessionId) extends OutgoingMessage

  final case class RemoteClientOpened(modelId: String, session: DomainUserSessionId) extends OutgoingMessage

  final case class RemoteClientResyncStarted(modelId: String, session: DomainUserSessionId) extends OutgoingMessage

  final case class RemoteClientResyncCompleted(modelId: String, session: DomainUserSessionId) extends OutgoingMessage

  final case class ModelResyncServerComplete(modelId: String,
                                             connectedClients: Set[DomainUserSessionId],
                                             resyncingClients: Set[DomainUserSessionId],
                                             references: Set[ReferenceState]) extends OutgoingMessage

  object ForceModelCloseReasonCode extends Enumeration {
    val Unknown, Unauthorized, Deleted, ErrorApplyingOperation, InvalidReferenceEvent, PermissionError, UnexpectedCommittedVersion, PermissionsChanged = Value
  }

  final case class ModelForceClose(modelId: String, reason: String, reasonCode: ForceModelCloseReasonCode.Value) extends OutgoingMessage

  final case class ModelPermissionsChanged(modelId: String, permissions: ModelPermissions) extends OutgoingMessage

  final case class ClientAutoCreateModelConfigRequest(modelId: String, autoConfigId: Int, replyTo: ActorRef[ClientAutoCreateModelConfigResponse]) extends OutgoingMessage


  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ClientAutoCreateModelConfigTimeout], name = "timeout"),
    new JsonSubTypes.Type(value = classOf[ClientAutoCreateModelConfigInvalid], name = "invalid"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait ClientAutoCreateModelConfigError

  final case class ClientAutoCreateModelConfigTimeout() extends ClientAutoCreateModelConfigError

  final case class ClientAutoCreateModelConfigInvalid() extends ClientAutoCreateModelConfigError

  final case class UnknownError() extends ClientAutoCreateModelConfigError

  final case class ClientAutoCreateModelConfigResponse(config: Either[ClientAutoCreateModelConfigError, ClientAutoCreateModelConfig]) extends CborSerializable

  final case class ClientAutoCreateModelConfig(collectionId: String,
                                               modelData: Option[ObjectValue],
                                               overridePermissions: Option[Boolean],
                                               worldPermissions: Option[ModelPermissions],
                                               userPermissions: Map[DomainUserId, ModelPermissions],
                                               ephemeral: Option[Boolean])

  sealed trait RemoteReferenceEvent extends OutgoingMessage

  final case class RemoteReferenceShared(modelId: String, session: DomainUserSessionId,
                                         id: Option[String],
                                         key: String,
                                         values: ModelReferenceValues) extends RemoteReferenceEvent

  final case class RemoteReferenceSet(modelId: String,
                                      session: DomainUserSessionId,
                                      id: Option[String],
                                      key: String,
                                      values: ModelReferenceValues) extends RemoteReferenceEvent

  final case class RemoteReferenceCleared(modelId: String, session: DomainUserSessionId, id: Option[String], key: String) extends RemoteReferenceEvent

  final case class RemoteReferenceUnshared(modelId: String, session: DomainUserSessionId, id: Option[String], key: String) extends RemoteReferenceEvent

  private final case class ModelClosed(modelId: String, resourceId: String, cb: ReplyCallback) extends Message

  private final case class ModelOpenSuccess(modelId: String, message: RealtimeModelActor.OpenModelSuccess, cb: ReplyCallback) extends Message

  private final case class ModelOpenFailure(modelId: String, failure: Either[Throwable, RealtimeModelActor.OpenRealtimeModelError], cb: ReplyCallback) extends Message


  private final case class ModelResyncRequestSuccess(modelId: String, data: RealtimeModelActor.ModelResyncResponseData, cb: ReplyCallback) extends Message

  private final case class ModelResyncRequestFailure(modelId: String, failure: Either[Throwable, RealtimeModelActor.ModelResyncError], cb: ReplyCallback) extends Message

}

