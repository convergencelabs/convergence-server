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

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props, actorRef2Scala}
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.proto.model.ModelOfflineSubscriptionChangeRequestMessage.ModelOfflineSubscriptionData
import com.convergencelabs.convergence.proto.model.ModelsQueryResponseMessage.ModelResult
import com.convergencelabs.convergence.proto.model.OfflineModelUpdatedMessage.{ModelUpdateData, OfflineModelInitialData, OfflineModelUpdateData}
import com.convergencelabs.convergence.proto.model.{ModelResyncCompleteRequestMessage, _}
import com.convergencelabs.convergence.proto.{ModelMessage => ProtoModelMessage, _}
import com.convergencelabs.convergence.server.api.realtime.ImplicitMessageConversions.{instanceToTimestamp, messageToObjectValue, modelPermissionsToMessage, modelUserPermissionSeqToMap, objectValueToMessage}
import com.convergencelabs.convergence.server.api.realtime.ModelClientActor._
import com.convergencelabs.convergence.server.datastore.domain.ModelStoreActor._
import com.convergencelabs.convergence.server.datastore.domain.{ModelPermissions, QueryParsingException}
import com.convergencelabs.convergence.server.domain.model.{RemoteClientResyncStarted, _}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserSessionId, UnauthorizedException}
import com.convergencelabs.convergence.server.util.concurrent.AskFuture

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * The [[ModelClientActor]] handles all incoming and outgoing messages
 * that are specific to the Model subsystem.
 *
 * @param domainId        The domain this client has connected to.
 * @param session         The session that has connected to the domain.
 * @param modelStoreActor The model persistence store from this domain.
 * @param requestTimeout  The default request timeout.
 */
private[realtime] class ModelClientActor(private[this] val domainId: DomainId,
                                         private[this] implicit val session: DomainUserSessionId,
                                         private[this] val modelStoreActor: ActorRef,
                                         private[this] implicit val requestTimeout: Timeout,
                                         private[this] val offlineModelSyncInterval: FiniteDuration)
  extends Actor with ActorLogging {

  private[this] implicit val ec: ExecutionContextExecutor = context.dispatcher

  private[this] val modelClusterRegion: ActorRef = RealtimeModelSharding.shardRegion(context.system)

  private[this] var nextResourceId = 0
  private[this] var resourceIdToModelId = Map[String, String]()
  private[this] var modelIdToResourceId = Map[String, String]()
  private[this] var subscribedModels = Map[String, OfflineModelState]()

  private[this] val offlineSyncTask = context.system.scheduler.schedule(offlineModelSyncInterval, offlineModelSyncInterval, () => self ! SyncOfflineModels)

  def receive: Receive = {
    case MessageReceived(message) if message.isInstanceOf[NormalMessage with ProtoModelMessage] =>
      onMessageReceived(message.asInstanceOf[NormalMessage with ProtoModelMessage])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[RequestMessage with ProtoModelMessage] =>
      onRequestReceived(message.asInstanceOf[RequestMessage with ProtoModelMessage], replyPromise)
    case message: RealtimeModelClientMessage =>
      onOutgoingModelMessage(message)
    case SyncOfflineModels =>
      syncOfflineModels(this.subscribedModels)
    case message: UpdateOfflineModel =>
      handleOfflineModelSynced(message)
    case x: Any =>
      unhandled(x)
  }

  override def postStop(): Unit = {
    super.postStop()
    this.offlineSyncTask.cancel()
  }

  private[this] def handleOfflineModelSynced(message: UpdateOfflineModel): Unit = {
    val UpdateOfflineModel(modelId, action) = message
    action match {
      case OfflineModelInitial(model, permissions, valueIdPrefix) =>
        this.subscribedModels.get(modelId).foreach { currentState =>
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

          context.parent ! message

          val version = model.metaData.version
          this.subscribedModels += modelId -> OfflineModelState(version, permissions)
        }
      case OfflineModelUpdated(model, permissions) =>
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
          context.parent ! message

          val version = model.map(_.metaData.version).getOrElse(currentState.currentVersion)
          val perms = permissions.getOrElse(currentState.currentPermissions)
          this.subscribedModels += modelId -> OfflineModelState(version, perms)

        }

      case OfflineModelDeleted() =>
        val message = OfflineModelUpdatedMessage(modelId, OfflineModelUpdatedMessage.Action.Deleted(true))
        context.parent ! message

        this.subscribedModels -= modelId

      case OfflineModelPermissionRevoked() =>
        val message = OfflineModelUpdatedMessage(modelId, OfflineModelUpdatedMessage.Action.PermissionRevoked(true))
        context.parent ! message

        this.subscribedModels -= modelId
      case OfflineModelNotUpdate() =>
      // No update required
    }
  }

  private[this] def syncOfflineModels(models: Map[String, OfflineModelState]): Unit = {
    val notOpen = models.filter { case (modelId, _) => !this.modelIdToResourceId.contains(modelId) }

    notOpen.foreach { case (modelId, OfflineModelState(version, permissions)) =>
      val request = GetModelUpdateRequest(modelId, version, permissions, this.session.userId)
      val response = modelStoreActor ? request
      response.mapTo[OfflineModelUpdateAction] onComplete {
        case Success(action) =>
          self ! UpdateOfflineModel(modelId, action)
        case Failure(cause) =>
          log.error(cause, "Error updating offline model")
      }
    }
  }

  //
  // Outgoing Messages
  //
  private[this] def onOutgoingModelMessage(event: RealtimeModelClientMessage): Unit = {
    event match {
      case op: OutgoingOperation => onOutgoingOperation(op)
      case opAck: OperationAcknowledgement => onOperationAcknowledgement(opAck)
      case remoteOpened: RemoteClientOpened => onRemoteClientOpened(remoteOpened)
      case remoteClosed: RemoteClientClosed => onRemoteClientClosed(remoteClosed)
      case forceClosed: ModelForceClose => onModelForceClose(forceClosed)
      case autoCreateRequest: ClientAutoCreateModelConfigRequest => onAutoCreateModelConfigRequest(autoCreateRequest)
      case refShared: RemoteReferenceShared => onRemoteReferenceShared(refShared)
      case refUnshared: RemoteReferenceUnshared => onRemoteReferenceUnshared(refUnshared)
      case refSet: RemoteReferenceSet => onRemoteReferenceSet(refSet)
      case refCleared: RemoteReferenceCleared => onRemoteReferenceCleared(refCleared)
      case permsChanged: ModelPermissionsChanged => onModelPermissionsChanged(permsChanged)
      case resyncStarted: RemoteClientResyncStarted => onRemoteClientResyncStarted(resyncStarted)
      case resyncCompleted: RemoteClientResyncCompleted => onRemoteClientResyncCompleted(resyncCompleted)
    }
  }

  private[this] def onOutgoingOperation(op: OutgoingOperation): Unit = {
    val OutgoingOperation(modelId, session, contextVersion, timestamp, operation) = op
    resourceId(modelId) foreach { resourceId =>
      context.parent ! RemoteOperationMessage(
        resourceId,
        session.sessionId,
        contextVersion,
        Some(timestamp),
        Some(OperationMapper.mapOutgoing(operation)))

      this.subscribedModels.get(modelId).foreach(state => {
        val newState = state.copy(currentVersion = contextVersion)
        this.subscribedModels += (modelId -> newState)
      })
    }
  }

  private[this] def onOperationAcknowledgement(opAck: OperationAcknowledgement): Unit = {
    val OperationAcknowledgement(modelId, seqNo, version, timestamp) = opAck
    resourceId(modelId) foreach { resourceId =>
      context.parent ! OperationAcknowledgementMessage(resourceId, seqNo, version, Some(timestamp))
    }
  }

  private[this] def onRemoteClientOpened(opened: RemoteClientOpened): Unit = {
    val RemoteClientOpened(modelId, session) = opened
    resourceId(modelId) foreach { resourceId =>
      context.parent ! RemoteClientOpenedMessage(resourceId, session.sessionId)
    }
  }

  private[this] def onRemoteClientClosed(closed: RemoteClientClosed): Unit = {
    val RemoteClientClosed(modelId, session) = closed
    resourceId(modelId) foreach { resourceId =>
      context.parent ! RemoteClientClosedMessage(resourceId, session.sessionId)
    }
  }

  private[this] def onModelPermissionsChanged(permsChanged: ModelPermissionsChanged): Unit = {
    val ModelPermissionsChanged(modelId, permissions) = permsChanged
    resourceId(modelId) foreach { resourceId =>
      context.parent ! ModelPermissionsChangedMessage(resourceId, Some(permissions))

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
      context.parent ! ModelForceCloseMessage(resourceId, reason, reasonCode.id)
    }
  }

  private[this] def onAutoCreateModelConfigRequest(autoConfigRequest: ClientAutoCreateModelConfigRequest): Unit = {
    val ClientAutoCreateModelConfigRequest(_, autoConfigId) = autoConfigRequest
    val askingActor = sender
    val future = context.parent ? AutoCreateModelConfigRequestMessage(autoConfigId)
    future.mapResponse[AutoCreateModelConfigResponseMessage] onComplete {
      case Success(AutoCreateModelConfigResponseMessage(collection, data, overridePermissions, worldPermissionsData, userPermissionsData, ephemeral)) =>
        val worldPermissions = worldPermissionsData.map {
          case ModelPermissionsData(read, write, remove, manage) =>
            ModelPermissions(read, write, remove, manage)
        }

        val userPermissions = modelUserPermissionSeqToMap(userPermissionsData)
        val response = ClientAutoCreateModelConfigResponse(
          collection,
          data.map(messageToObjectValue),
          Some(overridePermissions),
          worldPermissions,
          userPermissions,
          Some(ephemeral))
        askingActor ! response
      case Failure(cause) =>
        // forward the failure to the asking actor, so we fail fast.
        askingActor ! akka.actor.Status.Failure(cause)
    }
  }

  private[this] def onRemoteReferenceShared(refShared: RemoteReferenceShared): Unit = {
    val RemoteReferenceShared(modelId, session, valueId, key, refType, values) = refShared
    resourceId(modelId) foreach { resourceId =>
      val references = mapOutgoingReferenceValue(refType, values)
      context.parent ! RemoteReferenceSharedMessage(resourceId, valueId, key, Some(references), session.sessionId)
    }
  }

  private[this] def onRemoteReferenceUnshared(refUnshared: RemoteReferenceUnshared): Unit = {
    val RemoteReferenceUnshared(modelId, session, valueId, key) = refUnshared
    resourceId(modelId) foreach { resourceId =>
      context.parent ! RemoteReferenceUnsharedMessage(resourceId, valueId, key, session.sessionId)
    }
  }

  private[this] def onRemoteReferenceSet(refSet: RemoteReferenceSet): Unit = {
    val RemoteReferenceSet(modelId, session, valueId, key, refType, values) = refSet
    resourceId(modelId) foreach { resourceId =>
      val references = mapOutgoingReferenceValue(refType, values)
      context.parent ! RemoteReferenceSetMessage(resourceId, valueId, key, Some(references), session.sessionId)
    }
  }

  private[this] def onRemoteClientResyncStarted(message: RemoteClientResyncStarted): Unit = {
    val RemoteClientResyncStarted(modelId, remoteSession) = message
    resourceId(modelId) foreach { resourceId =>
      val outgoing = RemoteClientResyncStartedMessage(resourceId, remoteSession.sessionId)
      context.parent ! outgoing
    }
  }

  private[this] def onRemoteClientResyncCompleted(message: RemoteClientResyncCompleted): Unit = {
    val RemoteClientResyncCompleted(modelId, remoteSession) = message
    resourceId(modelId) foreach { resourceId =>
      val outgoing = RemoteClientResyncCompletedMessage(resourceId, remoteSession.sessionId)
      context.parent ! outgoing
    }
  }

  private[this] def mapOutgoingReferenceValue(refType: ReferenceType.Value, values: Any): ReferenceValues = {
    refType match {
      case ReferenceType.Index =>
        val indices = values.asInstanceOf[List[Int]]
        ReferenceValues().withIndices(Int32List(indices))
      case ReferenceType.Range =>
        val ranges = values.asInstanceOf[List[(Int, Int)]].map {
          case (from, to) => IndexRange(from, to)
        }
        ReferenceValues().withRanges(IndexRangeList(ranges))
      case ReferenceType.Property =>
        val properties = values.asInstanceOf[List[String]]
        ReferenceValues().withProperties(StringList(properties))
      case ReferenceType.Element =>
        val elements = values.asInstanceOf[List[String]]
        ReferenceValues().withElements(StringList(elements))
    }
  }

  private[this] def mapIncomingReference(values: ReferenceValues): (ReferenceType.Value, List[Any]) = {
    values.values match {
      case ReferenceValues.Values.Indices(Int32List(indices)) =>
        (ReferenceType.Index, indices.toList)
      case ReferenceValues.Values.Ranges(IndexRangeList(ranges)) =>
        (ReferenceType.Range, ranges.map(r => (r.startIndex, r.endIndex)).toList)
      case ReferenceValues.Values.Properties(StringList(properties)) =>
        (ReferenceType.Property, properties.toList)
      case ReferenceValues.Values.Elements(StringList(elements)) =>
        (ReferenceType.Element, elements.toList)
      case ReferenceValues.Values.Empty =>
        ???
    }
  }

  private[this] def onRemoteReferenceCleared(refCleared: RemoteReferenceCleared): Unit = {
    val RemoteReferenceCleared(modelId, session, valueId, key) = refCleared
    resourceId(modelId) foreach { resourceId =>
      context.parent ! RemoteReferenceClearedMessage(resourceId, valueId, key, session.sessionId)
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
      case resyncCompleteRequest: ModelResyncCompleteRequestMessage =>
        onModelResyncCompleteRequest(resyncCompleteRequest, replyCallback)
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

  private[this] def onMessageReceived(message: NormalMessage with ProtoModelMessage): Unit = {
    message match {
      case message: OperationSubmissionMessage => onOperationSubmission(message)
      case message: ShareReferenceMessage => onShareReference(message)
      case message: UnshareReferenceMessage => onUnshareReference(message)
      case message: SetReferenceMessage => onSetReference(message)
      case message: ClearReferenceMessage => onClearReference(message)
    }
  }

  private[this] def onOperationSubmission(message: OperationSubmissionMessage): Unit = {
    val OperationSubmissionMessage(resourceId, seqNo, version, operation) = message
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val submission = OperationSubmission(
          domainId, modelId, seqNo, version, OperationMapper.mapIncoming(operation.get))
        modelClusterRegion ! submission
      case None =>
        log.warning(s"$domainId: Received an operation submissions for a resource id that does not exists.")
        sender ! ErrorMessage("model_not_open", "An operation message was received for a model that is not open", Map())
    }
  }

  private[this] def onShareReference(message: ShareReferenceMessage): Unit = {
    val ShareReferenceMessage(resourceId, valueId, key, references, version) = message
    val vId = valueId.filter(!_.isEmpty)
    val (refType, values) = mapIncomingReference(references.get)
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val publishReference = ShareReference(domainId, modelId, vId, key, refType, values, version)
        modelClusterRegion ! publishReference
      case None =>
        log.warning(s"$domainId: Received a reference publish message for a resource id that does not exists.")
        sender ! ErrorMessage("model_not_open", "An reference message was received for a model that is not open", Map())
    }
  }

  def onUnshareReference(message: UnshareReferenceMessage): Unit = {
    val UnshareReferenceMessage(resourceId, valueId, key) = message
    val vId = valueId.filter(!_.isEmpty)
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val unshareReference = UnshareReference(domainId, modelId, vId, key)
        modelClusterRegion ! unshareReference
      case None =>
        log.warning(s"$domainId: Received a reference unshare message for a resource id that does not exists.")
        sender ! ErrorMessage("model_not_open", "An reference message was received for a model that is not open", Map())
    }
  }

  private[this] def onSetReference(message: SetReferenceMessage): Unit = {
    val SetReferenceMessage(resourceId, valueId, key, references, version) = message
    val vId = valueId.filter(!_.isEmpty)
    // FIXME handle none
    val (referenceType, values) = mapIncomingReference(references.get)
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val setReference = SetReference(domainId, modelId, vId, key, referenceType, values, version)
        modelClusterRegion ! setReference
      case None =>
        log.warning(s"$domainId: Received a reference set message for a resource id that does not exists.")
        sender ! ErrorMessage("model_not_open", "An reference message was received for a model that is not open", Map())
    }
  }

  private[this] def onClearReference(message: ClearReferenceMessage): Unit = {
    val ClearReferenceMessage(resourceId, valueId, key) = message
    val vId = valueId.filter(!_.isEmpty)
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val clearReference = ClearReference(domainId, modelId, vId, key)
        modelClusterRegion ! clearReference
      case None =>
        log.warning(s"$domainId: Received a reference clear message for a resource id that does not exists.")
        sender ! ErrorMessage("model_not_open", "An reference message was received for a model that is not open", Map())
    }
  }

  private[this] def onModelOfflineSubscription(message: ModelOfflineSubscriptionChangeRequestMessage, replyCallback: ReplyCallback): Unit = {
    val ModelOfflineSubscriptionChangeRequestMessage(subscribe, unsubscribe, all) = message

    val previousModels = this.subscribedModels.keySet

    if (all) {
      this.subscribedModels = Map()
    }

    unsubscribe.foreach(modelId => this.subscribedModels -= modelId)

    subscribe.foreach { case ModelOfflineSubscriptionData(modelId, version, permissions) =>
      val ModelPermissionsData(read, write, remove, manage) =
        permissions.getOrElse(ModelPermissionsData(false, false, false, false))
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
    val CloseRealtimeModelRequestMessage(resourceId) = request
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val future = modelClusterRegion ? CloseRealtimeModelRequest(domainId, modelId, session)
        future.mapTo[Unit] onComplete {
          case Success(()) =>
            resourceIdToModelId -= resourceId
            modelIdToResourceId -= modelId
            cb.reply(CloseRealTimeModelResponseMessage())
          case Failure(cause) =>
            log.error(cause, s"$domainId: Unexpected error closing model.")
            cb.unexpectedError("could not close model")
        }
      case None =>
        cb.expectedError("model_not_open", s"the requested model was not open")
    }
  }

  private[this] def onOpenRealtimeModelRequest(request: OpenRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val OpenRealtimeModelRequestMessage(optionalModelId, autoCreateId) = request
    val modelId = optionalModelId.filter(!_.isEmpty).getOrElse(UUID.randomUUID().toString)
    val openRequest = OpenRealtimeModelRequest(domainId, modelId, autoCreateId, session, self)
    val future = modelClusterRegion ? openRequest
    future.mapResponse[OpenModelSuccess] onComplete {
      case Success(OpenModelSuccess(valueIdPrefix, metaData, connectedClients, resyncingClients, references, modelData, modelPermissions)) =>
        val resourceId = generateNextResourceId()
        resourceIdToModelId += (resourceId -> modelId)
        modelIdToResourceId += (modelId -> resourceId)

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
      case Failure(ModelAlreadyOpenException()) =>
        cb.reply(ModelClientActor.ModelAlreadyOpenError)
      case Failure(ModelDeletedWhileOpeningException()) =>
        cb.reply(ModelClientActor.ModelDeletedError)
      case Failure(ClientDataRequestFailure(message)) =>
        cb.expectedError("data_request_failure", message)
      case Failure(ModelNotFoundException(_)) =>
        cb.reply(ModelClientActor.ModelNotFoundError)
      case Failure(UnauthorizedException(message)) =>
        cb.reply(ErrorMessages.Unauthorized(message))
      case Failure(cause) =>
        log.error(cause, s"$domainId/$modelId: Unexpected error opening model.")
        cb.unknownError()
    }
  }

  private[this] def convertReferences(references: Set[ReferenceState]): Seq[ReferenceData] = {
    references.map {
      case ReferenceState(sessionId, valueId, key, refType, values) =>
        val referenceValues = mapOutgoingReferenceValue(refType, values)
        ReferenceData(sessionId.sessionId, valueId, key, Some(referenceValues))
    }.toSeq
  }

  private[this] def onModelResyncRequest(request: ModelResyncRequestMessage, cb: ReplyCallback): Unit = {
    val ModelResyncRequestMessage(modelId, contextVersion) = request

    val reconnectRequest = ModelResyncRequest(domainId, modelId, this.session, contextVersion, this.self)

    val future = modelClusterRegion ? reconnectRequest
    future.mapResponse[ModelResyncResponse] onComplete {
      case Success(ModelResyncResponse(currentVersion, permissions)) =>
        val resourceId = generateNextResourceId()
        resourceIdToModelId += (resourceId -> modelId)
        modelIdToResourceId += (modelId -> resourceId)
        val ModelPermissions(read, write, remove, manage) = permissions
        val permissionData = ModelPermissionsData(read, write, remove, manage)
        val responseMessage = ModelResyncResponseMessage(resourceId, currentVersion, Some(permissionData))
        cb.reply(responseMessage)
      case Failure(ModelAlreadyOpenException()) =>
        cb.reply(ModelClientActor.ModelAlreadyOpenError)
      case Failure(ModelNotFoundException(_)) =>
        cb.reply(ModelClientActor.ModelNotFoundError)
      case Failure(UnauthorizedException(message)) =>
        cb.reply(ErrorMessages.Unauthorized(message))
      case Failure(cause) =>
        log.error(cause, s"$domainId/$modelId: Unexpected error reconnecting model.")
        cb.unknownError()
    }
  }

  private[this] def onModelResyncCompleteRequest(message: ModelResyncCompleteRequestMessage, cb: ReplyCallback): Unit = {
    val ModelResyncCompleteRequestMessage(resourceId, open) = message
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val request = ModelResyncCompleteRequest(domainId, modelId, session, open)

        val future = modelClusterRegion ? request
        future.mapResponse[ModelResyncCompleteResponse] onComplete {
          case Success(ModelResyncCompleteResponse(connectedClients, resyncingClients, references)) =>
            val convertedReferences = convertReferences(references)
            val responseMessage = ModelResyncCompleteResponseMessage(
              connectedClients.map(s => s.sessionId).toSeq,
              resyncingClients.map(s => s.sessionId).toSeq,
              convertedReferences)
            cb.reply(responseMessage)
          case Failure(cause) =>
            log.error(cause, "Error completing model sync")
            cb.unexpectedError("Could not complete the model resynchronization")
        }
      case None =>
        cb.expectedError("resource_not_found", "The requested resource id was not found")
    }
  }


  private[this] def onCreateRealtimeModelRequest(request: CreateRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val CreateRealtimeModelRequestMessage(collectionId, optionalModelId, data, overridePermissions, worldPermissionsData, userPermissionsData) = request
    val worldPermissions = worldPermissionsData.map(w =>
      ModelPermissions(w.read, w.write, w.remove, w.manage))

    val userPermissions = modelUserPermissionSeqToMap(userPermissionsData)

    // FIXME make a utility for this.
    val modelId = optionalModelId.filter(!_.isEmpty).getOrElse(UUID.randomUUID().toString)

    val future = modelClusterRegion ? CreateRealtimeModel(
      domainId,
      modelId,
      collectionId,
      data.get,
      Some(overridePermissions),
      worldPermissions,
      userPermissions,
      Some(session))
    future.mapResponse[String] onComplete {
      case Success(_) =>
        cb.reply(CreateRealtimeModelResponseMessage(modelId))
      case Failure(ModelAlreadyExistsException(_)) =>
        cb.expectedError("model_already_exists", s"A model with the id '$modelId' already exists")
      case Failure(UnauthorizedException(message)) =>
        cb.reply(ErrorMessages.Unauthorized(message))
      case Failure(cause) =>
        log.error(cause, s"$domainId: Unexpected error creating model.")
        cb.unexpectedError("could not create model")
    }
  }

  private[this] def onDeleteRealtimeModelRequest(request: DeleteRealtimeModelRequestMessage, cb: ReplyCallback): Unit

  = {
    val DeleteRealtimeModelRequestMessage(modelId) = request
    val future = modelClusterRegion ? DeleteRealtimeModel(domainId, modelId, Some(session))
    future.mapTo[Unit] onComplete {
      case Success(()) =>
        cb.reply(DeleteRealtimeModelResponseMessage())
      case Failure(ModelNotFoundException(_)) =>
        cb.reply(ModelClientActor.ModelNotFoundError)
      case Failure(UnauthorizedException(message)) =>
        cb.reply(ErrorMessages.Unauthorized(message))
      case Failure(cause) =>
        log.error(cause, s"$domainId: Unexpected error removing model.")
        cb.unexpectedError("Unexpected error removing model.")
    }
  }

  private[this] def onModelQueryRequest(request: ModelsQueryRequestMessage, cb: ReplyCallback): Unit = {
    val ModelsQueryRequestMessage(query) = request
    val future = modelStoreActor ? QueryModelsRequest(session.userId, query)
    future.mapResponse[PagedData[ModelQueryResult]] onComplete {
      case Success(result) =>
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
        // FIXME add paged info
        cb.reply(ModelsQueryResponseMessage(models, result.offset, result.count))
      case Failure(QueryParsingException(message, _, index)) =>
        cb.expectedError("invalid_query", message, Map("index" -> index.toString))
      case Failure(cause) =>
        log.error(cause, s"$domainId: Unexpected error querying models.")
        cb.unexpectedError("Unexpected error querying models.")
    }
  }

  private[this] def onGetModelPermissionsRequest(request: GetModelPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetModelPermissionsRequestMessage(modelId) = request
    val future = modelClusterRegion ? GetModelPermissionsRequest(domainId, modelId, session)
    future.mapResponse[GetModelPermissionsResponse] onComplete {
      case Success(GetModelPermissionsResponse(overridesCollection, world, users)) =>
        val mappedWorld = ModelPermissionsData(world.read, world.write, world.remove, world.manage)
        val mappedUsers = modelUserPermissionSeqToMap(users)
        cb.reply(GetModelPermissionsResponseMessage(overridesCollection, Some(mappedWorld), mappedUsers))
      case Failure(ModelNotFoundException(_)) =>
        cb.reply(ModelClientActor.ModelNotFoundError)
      case Failure(UnauthorizedException(message)) =>
        cb.reply(ErrorMessages.Unauthorized(message))
      case Failure(cause) =>
        log.error(cause, s"$domainId: Unexpected error getting permissions for model.")
        cb.unexpectedError("could get model permissions")
    }
  }

  private[this] def onSetModelPermissionsRequest(request: SetModelPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val SetModelPermissionsRequestMessage(modelId, overridePermissions, world, setAllUsers, addedUsers, removedUsers) = request
    val mappedWorld = world map (w => ModelPermissions(w.read, w.write, w.remove, w.manage))
    val mappedAddedUsers = modelUserPermissionSeqToMap(addedUsers)

    val message = SetModelPermissionsRequest(
      domainId,
      modelId,
      session,
      overridePermissions,
      mappedWorld,
      setAllUsers,
      mappedAddedUsers,
      removedUsers.map(ImplicitMessageConversions.dataToDomainUserId).toList)
    val future = modelClusterRegion ? message
    future onComplete {
      case Success(_) =>
        cb.reply(SetModelPermissionsResponseMessage())
      case Failure(ModelNotFoundException(_)) =>
        cb.reply(ModelClientActor.ModelNotFoundError)
      case Failure(UnauthorizedException(m)) =>
        cb.reply(ErrorMessages.Unauthorized(m))
      case Failure(cause) =>
        log.error(cause, s"$domainId: Unexpected error setting permissions for model.")
        cb.unexpectedError("Unexpected error setting permissions for model.")
    }
  }

  private[this] def resourceId(modelId: String): Option[String] = {
    this.modelIdToResourceId.get(modelId) orElse {
      log.error(s"$domainId: Receive an outgoing message for a modelId that is not open: $modelId")
      None
    }
  }

  private[this] def generateNextResourceId(): String = {
    val id = nextResourceId.toString
    nextResourceId += 1
    id
  }
}

private[realtime] object ModelClientActor {
  def props(domainFqn: DomainId,
            session: DomainUserSessionId,
            modelStoreActor: ActorRef,
            requestTimeout: Timeout,
            offlineModelSyncInterval: FiniteDuration): Props =
    Props(new ModelClientActor(domainFqn, session, modelStoreActor, requestTimeout, offlineModelSyncInterval))

  private val ModelNotFoundError = ErrorMessage("model_not_found", "A model with the specified collection and model id does not exist.", Map())
  private val ModelAlreadyOpenError = ErrorMessage("model_already_open", "The requested model is already open by this client.", Map())
  private val ModelDeletedError = ErrorMessage("model_deleted", "The requested model was deleted.", Map())

  private case object SyncOfflineModels

  private case class OfflineModelState(currentVersion: Long, currentPermissions: ModelPermissions)

  private case class UpdateOfflineModel(modelId: String, action: OfflineModelUpdateAction)

}

