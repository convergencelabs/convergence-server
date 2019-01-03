package com.convergencelabs.server.frontend.realtime

import java.util.UUID

import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.datastore.domain.QueryParsingException
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.UnauthorizedException
import com.convergencelabs.server.domain.model.ClearReference
import com.convergencelabs.server.domain.model.ClientAutoCreateModelConfigRequest
import com.convergencelabs.server.domain.model.ClientAutoCreateModelConfigResponse
import com.convergencelabs.server.domain.model.ClientDataRequestFailure
import com.convergencelabs.server.domain.model.CloseRealtimeModelRequest
import com.convergencelabs.server.domain.model.CreateRealtimeModel
import com.convergencelabs.server.domain.model.DeleteRealtimeModel
import com.convergencelabs.server.domain.model.GetModelPermissionsRequest
import com.convergencelabs.server.domain.model.GetModelPermissionsResponse
import com.convergencelabs.server.domain.model.ModelAlreadyExistsException
import com.convergencelabs.server.domain.model.ModelAlreadyOpenException
import com.convergencelabs.server.domain.model.ModelDeletedWhileOpeningException
import com.convergencelabs.server.domain.model.ModelForceClose
import com.convergencelabs.server.domain.model.ModelNotFoundException
import com.convergencelabs.server.domain.model.ModelPermissionsChanged
import com.convergencelabs.server.domain.model.OpenModelSuccess
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest
import com.convergencelabs.server.domain.model.OperationAcknowledgement
import com.convergencelabs.server.domain.model.OperationSubmission
import com.convergencelabs.server.domain.model.OutgoingOperation
import com.convergencelabs.server.domain.model.PublishReference
import com.convergencelabs.server.domain.model.QueryModelsRequest
import com.convergencelabs.server.domain.model.QueryModelsResponse
import com.convergencelabs.server.domain.model.RealtimeModelClientMessage
import com.convergencelabs.server.domain.model.RealtimeModelSharding
import com.convergencelabs.server.domain.model.ReferenceState
import com.convergencelabs.server.domain.model.ReferenceType
import com.convergencelabs.server.domain.model.RemoteClientClosed
import com.convergencelabs.server.domain.model.RemoteClientOpened
import com.convergencelabs.server.domain.model.RemoteReferenceCleared
import com.convergencelabs.server.domain.model.RemoteReferencePublished
import com.convergencelabs.server.domain.model.RemoteReferenceSet
import com.convergencelabs.server.domain.model.RemoteReferenceUnpublished
import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.model.SetModelPermissionsRequest
import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.UnpublishReference
import com.convergencelabs.server.util.concurrent.AskFuture
import com.convergencelabs.server.frontend.realtime.ImplicitMessageConversions._

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.util.Timeout

import com.convergencelabs.server.domain.model.ModelAlreadyExistsException
import com.convergencelabs.server.frontend.rest.DataValueToJValue
import com.convergencelabs.server.datastore.domain.QueryParsingException
import com.convergencelabs.server.domain.DomainFqn
import akka.cluster.sharding.ClusterSharding
import java.util.UUID
import com.convergencelabs.server.datastore.domain.QueryParsingException
import com.convergencelabs.server.domain.model.ModelDeletedWhileOpeningException
import com.convergencelabs.server.domain.model.ModelAlreadyOpenException
import com.convergencelabs.server.domain.model.CreateRealtimeModel
import com.convergencelabs.server.domain.model.DeleteRealtimeModel
import com.convergencelabs.server.util.BiMap
import com.convergencelabs.server.domain.model.RealtimeModelSharding

import io.convergence.proto.Normal
import io.convergence.proto.Model
import io.convergence.proto.Request
import io.convergence.proto.common.ErrorMessage
import io.convergence.proto.model.GetModelPermissionsRequestMessage
import io.convergence.proto.model.OpenRealtimeModelResponseMessage
import io.convergence.proto.references.RemoteReferenceSetMessage
import io.convergence.proto.references.SetReferenceMessage
import io.convergence.proto.model.RemoteClientOpenedMessage
import io.convergence.proto.model.SetModelPermissionsResponseMessage
import io.convergence.proto.references.ShareReferenceMessage
import io.convergence.proto.operations.OperationAcknowledgementMessage
import io.convergence.proto.model.CloseRealTimeModelSuccessMessage
import io.convergence.proto.references.UnshareReferenceMessage
import io.convergence.proto.model.ModelForceCloseMessage
import io.convergence.proto.model.SetModelPermissionsRequestMessage
import io.convergence.proto.references.ClearReferenceMessage
import io.convergence.proto.model.AutoCreateModelConfigResponseMessage
import io.convergence.proto.model.ModelPermissionsData
import io.convergence.proto.model.GetModelPermissionsResponseMessage
import io.convergence.proto.model.ModelsQueryRequestMessage
import io.convergence.proto.model.RemoteClientClosedMessage
import io.convergence.proto.references.RemoteReferenceUnsharedMessage
import io.convergence.proto.model.ModelResult
import io.convergence.proto.model.ModelPermissionsChangedMessage
import io.convergence.proto.model.CreateRealtimeModelRequestMessage
import io.convergence.proto.model.ModelsQueryResponseMessage
import io.convergence.proto.operations.RemoteOperationMessage
import io.convergence.proto.references.RemoteReferenceSharedMessage
import io.convergence.proto.model.OpenRealtimeModelRequestMessage
import io.convergence.proto.model.OpenRealtimeModelResponseMessage.ReferenceData
import io.convergence.proto.operations.OperationSubmissionMessage
import io.convergence.proto.model.CreateRealtimeModelSuccessMessage
import io.convergence.proto.model.DeleteRealtimeModelRequestMessage
import io.convergence.proto.model.DeleteRealtimeModelSuccessMessage
import io.convergence.proto.model.CloseRealtimeModelRequestMessage
import io.convergence.proto.model.AutoCreateModelConfigRequestMessage
import io.convergence.proto.references.RemoteReferenceClearedMessage
import io.convergence.proto.references.ReferenceValues
import io.convergence.proto.references.IndexRangeList
import io.convergence.proto.references.IndexRange
import io.convergence.proto.common.Int32List
import io.convergence.proto.common.StringList
import com.convergencelabs.server.frontend.realtime.ImplicitMessageConversions._
import com.google.protobuf.timestamp.Timestamp

object ModelClientActor {
  def props(
    domainFqn: DomainFqn,
    sk: SessionKey,
    modelQueryActor: ActorRef,
    requestTimeout: Timeout): Props =
    Props(new ModelClientActor(domainFqn, sk, modelQueryActor, requestTimeout))

  val ModelNotFoundError = ErrorMessage("model_not_found", "A model with the specifieid collection and model id does not exist.", Map())
}

class ModelClientActor(
  private[this] val domainFqn: DomainFqn,
  private[this] implicit val sessionKey: SessionKey,
  private[this] val modelQueryActor: ActorRef,
  private[this] implicit val requestTimeout: Timeout)
  extends Actor
  with ActorLogging {

  import ModelClientActor._
  import akka.pattern.ask
  import akka.pattern.ask

  private[this] var nextResourceId = 0;
  private[this] var resourceIdToModelId = Map[String, String]()
  private[this] var modelIdToResourceId = Map[String, String]()
  private[this] implicit val ec = context.dispatcher

  private[this] val modelClusterRegion: ActorRef = RealtimeModelSharding.shardRegion(context.system)

  def receive: Receive = {
    case MessageReceived(message) if message.isInstanceOf[Normal with Model] =>
      onMessageReceived(message.asInstanceOf[Normal with Model])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[Request with Model] =>
      onRequestReceived(message.asInstanceOf[Request with Model], replyPromise)
    case message: RealtimeModelClientMessage =>
      onOutgoingModelMessage(message)
    case x: Any =>
      unhandled(x)
  }

  //
  // Outgoing Messages
  //
  // scalastyle:off cyclomatic.complexity
  private[this] def onOutgoingModelMessage(event: RealtimeModelClientMessage): Unit = {
    event match {
      case op: OutgoingOperation => onOutgoingOperation(op)
      case opAck: OperationAcknowledgement => onOperationAcknowledgement(opAck)
      case remoteOpened: RemoteClientOpened => onRemoteClientOpened(remoteOpened)
      case remoteClosed: RemoteClientClosed => onRemoteClientClosed(remoteClosed)
      case foreceClosed: ModelForceClose => onModelForceClose(foreceClosed)
      case autoCreateRequest: ClientAutoCreateModelConfigRequest => onAutoCreateModelConfigRequest(autoCreateRequest)
      case refPublished: RemoteReferencePublished => onRemoteReferencePublished(refPublished)
      case refUnpublished: RemoteReferenceUnpublished => onRemoteReferenceUnpublished(refUnpublished)
      case refSet: RemoteReferenceSet => onRemoteReferenceSet(refSet)
      case refCleared: RemoteReferenceCleared => onRemoteReferenceCleared(refCleared)
      case permsChanged: ModelPermissionsChanged => onModelPermissionsChanged(permsChanged)
    }
  }
  // scalastyle:on cyclomatic.complexity

  private[this] def onOutgoingOperation(op: OutgoingOperation): Unit = {
    val OutgoingOperation(modelId, sessionKey, contextVersion, timestamp, operation) = op
    resourceId(modelId) foreach { resoruceId =>
      context.parent ! RemoteOperationMessage(
        resoruceId,
        sessionKey.sid,
        contextVersion,
        Some(timestamp),
        Some(OperationMapper.mapOutgoing(operation)))
    }
  }

  private[this] def onOperationAcknowledgement(opAck: OperationAcknowledgement): Unit = {
    val OperationAcknowledgement(modelId, seqNo, version, timestamp) = opAck
    resourceId(modelId) foreach { resourceId =>
      context.parent ! OperationAcknowledgementMessage(resourceId, seqNo, version, Some(timestamp))
    }
  }

  private[this] def onRemoteClientOpened(opened: RemoteClientOpened): Unit = {
    val RemoteClientOpened(modelId, sk) = opened
    resourceId(modelId) foreach { resourceId =>
      context.parent ! RemoteClientOpenedMessage(resourceId, sk.sid)
    }
  }

  private[this] def onRemoteClientClosed(closed: RemoteClientClosed): Unit = {
    val RemoteClientClosed(modelId, sk) = closed
    resourceId(modelId) foreach { resourceId =>
      context.parent ! RemoteClientClosedMessage(resourceId, sk.sid)
    }
  }

  private[this] def onModelPermissionsChanged(permsChanged: ModelPermissionsChanged): Unit = {
    val ModelPermissionsChanged(modelId, permissions) = permsChanged
    resourceId(modelId) foreach { resourceId =>
      val ModelPermissions(read, write, remove, manage) = permissions
      context.parent ! ModelPermissionsChangedMessage(resourceId, Some(permissions))
    }
  }

  private[this] def onModelForceClose(forceClose: ModelForceClose): Unit = {
    val ModelForceClose(modelId, reason) = forceClose
    resourceId(modelId) foreach { resourceId =>
      modelIdToResourceId -= modelId
      resourceIdToModelId -= resourceId
      context.parent ! ModelForceCloseMessage(resourceId, reason)
    }
  }

  private[this] def onAutoCreateModelConfigRequest(autoConfigRequest: ClientAutoCreateModelConfigRequest): Unit = {
    val ClientAutoCreateModelConfigRequest(modelId, autoConfigId) = autoConfigRequest
    val askingActor = sender
    val future = context.parent ? AutoCreateModelConfigRequestMessage(autoConfigId)
    future.mapResponse[AutoCreateModelConfigResponseMessage] onComplete {
      case Success(AutoCreateModelConfigResponseMessage(collection, data, overridePermissions, worldPermissionsData, userPermissionsData, ephemeral)) =>
        val worldPermissions = worldPermissionsData.map {
          case ModelPermissionsData(read, write, remove, manage) =>
            ModelPermissions(read, write, remove, manage)
        }

        val userPermissions = userPermissionsData.mapValues {
          case ModelPermissionsData(read, write, remove, manage) =>
            ModelPermissions(read, write, remove, manage)
        }

        askingActor ! ClientAutoCreateModelConfigResponse(
          collection,
          data.map(messageToObjectValue(_)),
          Some(overridePermissions),
          worldPermissions,
          userPermissions,
          Some(ephemeral))
      case Failure(cause) =>
        // forward the failure to the asker, so we fail fast.
        askingActor ! akka.actor.Status.Failure(cause)
    }
  }

  private[this] def onRemoteReferencePublished(refPublished: RemoteReferencePublished): Unit = {
    val RemoteReferencePublished(modelId, session, valueId, key, refType, values) = refPublished
    resourceId(modelId) foreach { resourceId =>
      val references = mapOutgoingReferenceValue(refType, values)
      context.parent ! RemoteReferenceSharedMessage(resourceId, valueId, key, Some(references), session.sid)
    }
  }

  private[this] def onRemoteReferenceUnpublished(refUnpublished: RemoteReferenceUnpublished): Unit = {
    val RemoteReferenceUnpublished(modelId, session, valueId, key) = refUnpublished
    resourceId(modelId) foreach { resourceId =>
      context.parent ! RemoteReferenceUnsharedMessage(resourceId, valueId, key, session.sid)
    }
  }

  private[this] def onRemoteReferenceSet(refSet: RemoteReferenceSet): Unit = {
    val RemoteReferenceSet(modelId, session, valueId, key, refType, values) = refSet
    resourceId(modelId) foreach { resourceId =>
      val references = mapOutgoingReferenceValue(refType, values)
      context.parent ! RemoteReferenceSetMessage(resourceId, valueId, key, Some(references), session.sid)
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
      case ReferenceValues.Values.Properties(StringList(proeprties)) =>
        (ReferenceType.Property, proeprties.toList)
      case ReferenceValues.Values.Elements(StringList(elements)) =>
        (ReferenceType.Element, elements.toList)
    }
  }

  private[this] def onRemoteReferenceCleared(refCleared: RemoteReferenceCleared): Unit = {
    val RemoteReferenceCleared(modelId, session, valueId, key) = refCleared
    resourceId(modelId) foreach { resourceId =>
      context.parent ! RemoteReferenceClearedMessage(resourceId, valueId, key, session.sid)
    }
  }

  //
  // Incoming Messages
  //

  private[this] def onRequestReceived(message: Request, replyCallback: ReplyCallback): Unit = {
    message match {
      case openRequest: OpenRealtimeModelRequestMessage => onOpenRealtimeModelRequest(openRequest, replyCallback)
      case closeRequest: CloseRealtimeModelRequestMessage => onCloseRealtimeModelRequest(closeRequest, replyCallback)
      case createRequest: CreateRealtimeModelRequestMessage => onCreateRealtimeModelRequest(createRequest, replyCallback)
      case deleteRequest: DeleteRealtimeModelRequestMessage => onDeleteRealtimeModelRequest(deleteRequest, replyCallback)
      case queryRequest: ModelsQueryRequestMessage => onModelQueryRequest(queryRequest, replyCallback)
      case getPermissionRequest: GetModelPermissionsRequestMessage => onGetModelPermissionsRequest(getPermissionRequest, replyCallback)
      case setPermissionRequest: SetModelPermissionsRequestMessage => onSetModelPermissionsRequest(setPermissionRequest, replyCallback)
    }
  }

  private[this] def onMessageReceived(message: Normal with Model): Unit = {
    message match {
      case submission: OperationSubmissionMessage => onOperationSubmission(submission)
      case publishReference: ShareReferenceMessage => onPublishReference(publishReference)
      case unpublishReference: UnshareReferenceMessage => onUnpublishReference(unpublishReference)
      case setReference: SetReferenceMessage => onSetReference(setReference)
      case clearReference: ClearReferenceMessage => onClearReference(clearReference)
    }
  }

  private[this] def onOperationSubmission(message: OperationSubmissionMessage): Unit = {
    val OperationSubmissionMessage(resourceId, seqNo, version, operation) = message
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val submission = OperationSubmission(
          domainFqn, modelId, seqNo, version, OperationMapper.mapIncoming(operation.get))
        modelClusterRegion ! submission
      case None =>
        log.warning(s"${domainFqn}: Recieved an operation submissions for a resource id that does not exists.")
        sender ! ErrorMessage("model_not_open", "An operation message was received for a model that is not open", Map())
    }
  }

  private[this] def onPublishReference(message: ShareReferenceMessage): Unit = {
    val ShareReferenceMessage(resourceId, id, key, references, version) = message
    val (refType, values) = mapIncomingReference(references.get)
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val publishReference = PublishReference(domainFqn, modelId, id, key, refType, values, version)
        modelClusterRegion ! publishReference
      case None =>
        log.warning(s"${domainFqn}: Recieved a reference publish message for a resource id that does not exists.")
        sender ! ErrorMessage("model_not_open", "An reference message was received for a model that is not open", Map())
    }
  }

  def onUnpublishReference(message: UnshareReferenceMessage): Unit = {
    val UnshareReferenceMessage(resourceId, id, key) = message
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val unpublishReference = UnpublishReference(domainFqn, modelId, id, key)
        modelClusterRegion ! unpublishReference
      case None =>
        log.warning(s"${domainFqn}: Recieved a reference unpublish message for a resource id that does not exists.")
        sender ! ErrorMessage("model_not_open", "An reference message was received for a model that is not open", Map())
    }
  }

  private[this] def onSetReference(message: SetReferenceMessage): Unit = {
    val SetReferenceMessage(resourceId, id, key, references, version) = message
    // FIXME handle none
    val (referenceType, values) = mapIncomingReference(references.get)
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val setReference = SetReference(domainFqn, modelId, id, key, referenceType, values, version)
        modelClusterRegion ! setReference
      case None =>
        log.warning(s"${domainFqn}: Recieved a reference set message for a resource id that does not exists.")
        sender ! ErrorMessage("model_not_open", "An reference message was received for a model that is not open", Map())
    }
  }

  private[this] def onClearReference(message: ClearReferenceMessage): Unit = {
    val ClearReferenceMessage(resourceId, id, key) = message
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val clearReference = ClearReference(domainFqn, modelId, id, key)
        modelClusterRegion ! clearReference
      case None =>
        log.warning(s"${domainFqn}: Recieved a reference clear message for a resource id that does not exists.")
        sender ! ErrorMessage("model_not_open", "An reference message was received for a model that is not open", Map())
    }
  }

  private[this] def onOpenRealtimeModelRequest(request: OpenRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val OpenRealtimeModelRequestMessage(optionalModelId, autoCreateId) = request

    val modelId = optionalModelId.getOrElse {
      UUID.randomUUID().toString()
    }

    val future = modelClusterRegion ? OpenRealtimeModelRequest(domainFqn, modelId, autoCreateId, sessionKey, self)
    future.mapResponse[OpenModelSuccess] onComplete {
      case Success(OpenModelSuccess(valueIdPrefix, metaData, connectedClients, references, modelData, modelPermissions)) =>
        val resourceId = nextResourceId();
        resourceIdToModelId += (resourceId -> modelId)
        modelIdToResourceId += (modelId -> resourceId)

        val convertedReferences = references.map {
          case ReferenceState(session, valueId, key, refType, values) =>
            val referenceValues = mapOutgoingReferenceValue(refType, values)
            ReferenceData(session.sid, valueId, key, Some(referenceValues))
        }

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
            connectedClients.map(s => s.sid).toSeq,
            convertedReferences.toSeq,
            Some(ModelPermissionsData(
              modelPermissions.read,
              modelPermissions.write,
              modelPermissions.remove,
              modelPermissions.manage))))
      case Failure(ModelAlreadyOpenException()) =>
        cb.expectedError("model_already_open", s"The requested model is already open by this client.")
      case Failure(ModelDeletedWhileOpeningException()) =>
        cb.expectedError("model_deleted", "The requested model was deleted while opening.")
      case Failure(ClientDataRequestFailure(message)) =>
        cb.expectedError("data_request_failure", message)
      case Failure(ModelNotFoundException(modelId)) =>
        cb.reply(ModelClientActor.ModelNotFoundError)
      case Failure(UnauthorizedException(message)) =>
        cb.reply(ErrorMessages.Unauthorized(message))
      case Failure(cause) =>
        log.error(cause, s"${domainFqn}/${modelId}: Unexpected error opening model.")
        cb.unknownError()
    }
  }

  private[this] def onCloseRealtimeModelRequest(request: CloseRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val CloseRealtimeModelRequestMessage(resourceId) = request
    resourceIdToModelId.get(resourceId) match {
      case Some(modelId) =>
        val future = modelClusterRegion ? CloseRealtimeModelRequest(domainFqn, modelId, sessionKey)
        future.mapTo[Unit] onComplete {
          case Success(()) =>
            resourceIdToModelId -= resourceId
            modelIdToResourceId -= modelId
            cb.reply(CloseRealTimeModelSuccessMessage())
          case Failure(cause) =>
            log.error(cause, s"${domainFqn}: Unexpected error closing model.")
            cb.unexpectedError("could not close model")
        }
      case None =>
        cb.expectedError("model_not_open", s"the requested model was not open")
    }
  }

  private[this] def onCreateRealtimeModelRequest(request: CreateRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val CreateRealtimeModelRequestMessage(collectionId, optionalModelId, data, overridePermissions, worldPermissionsData, userPermissionsData) = request
    val worldPermissions = worldPermissionsData.map(w =>
      ModelPermissions(w.read, w.write, w.remove, w.manage))

    val userPermissions = userPermissionsData.mapValues(p =>
      ModelPermissions(p.read, p.write, p.remove, p.manage))

    // FIXME make a utility for this.
    val modelId = optionalModelId.getOrElse(UUID.randomUUID().toString())

    val future = modelClusterRegion ? CreateRealtimeModel(
      domainFqn,
      modelId,
      collectionId,
      data.get,
      Some(overridePermissions),
      worldPermissions,
      userPermissions,
      Some(sessionKey))
    future.mapResponse[String] onComplete {
      case Success(modelId) =>
        cb.reply(CreateRealtimeModelSuccessMessage(modelId))
      case Failure(ModelAlreadyExistsException(modelId)) =>
        cb.expectedError("model_alread_exists", "A model with the specifieid model id already exists")
      case Failure(UnauthorizedException(message)) =>
        cb.reply(ErrorMessages.Unauthorized(message))
      case Failure(cause) =>
        log.error(cause, s"${domainFqn}: Unexpected error creating model.")
        cb.unexpectedError("could not create model")
    }
  }

  private[this] def onDeleteRealtimeModelRequest(request: DeleteRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val DeleteRealtimeModelRequestMessage(modelId) = request
    val future = modelClusterRegion ? DeleteRealtimeModel(domainFqn, modelId, Some(sessionKey))
    future.mapTo[Unit] onComplete {
      case Success(()) =>
        cb.reply(DeleteRealtimeModelSuccessMessage())
      case Failure(ModelNotFoundException(_)) =>
        cb.reply(ModelClientActor.ModelNotFoundError)
      case Failure(UnauthorizedException(message)) =>
        cb.reply(ErrorMessages.Unauthorized(message))
      case Failure(cause) =>
        log.error(cause, s"${domainFqn}: Unexpected error removing model.")
        cb.unexpectedError("Unexpected error removing model.")
    }
  }

  private[this] def onModelQueryRequest(request: ModelsQueryRequestMessage, cb: ReplyCallback): Unit = {
    val ModelsQueryRequestMessage(query) = request
    val future = modelQueryActor ? QueryModelsRequest(sessionKey, query)
    future.mapResponse[QueryModelsResponse] onComplete {
      case Success(QueryModelsResponse(result)) => cb.reply(
        ModelsQueryResponseMessage(result map {
          r =>
            ModelResult(
              r.meta.collectionId,
              r.meta.modelId,
              Some(r.meta.createdTime),
              Some(r.meta.modifiedTime),
              r.meta.version,
              Some(JsonProtoConverter.toStruct(r.data)))
        }))
      case Failure(QueryParsingException(message, query, index)) =>
        cb.expectedError("invalid_query", message, Map("index" -> index.toString))
      case Failure(cause) =>
        log.error(cause, s"${domainFqn}: Unexpected error querying models.")
        cb.unexpectedError("Unexpected error querying models.")
    }
  }

  private[this] def onGetModelPermissionsRequest(request: GetModelPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetModelPermissionsRequestMessage(modelId) = request
    val future = modelClusterRegion ? GetModelPermissionsRequest(domainFqn, modelId, sessionKey)
    future.mapResponse[GetModelPermissionsResponse] onComplete {
      case Success(GetModelPermissionsResponse(overridesCollection, world, users)) =>
        val mappedWorld = ModelPermissionsData(world.read, world.write, world.remove, world.manage)
        val mappedUsers = users mapValues {
          case ModelPermissions(read, write, remove, manage) =>
            ModelPermissionsData(read, write, remove, manage)
        }
        cb.reply(GetModelPermissionsResponseMessage(overridesCollection, Some(mappedWorld), mappedUsers))
      case Failure(ModelNotFoundException(_)) =>
        cb.reply(ModelClientActor.ModelNotFoundError)
      case Failure(UnauthorizedException(message)) =>
        cb.reply(ErrorMessages.Unauthorized(message))
      case Failure(cause) =>
        log.error(cause, s"${domainFqn}: Unexpected error getting permissions for model.")
        cb.unexpectedError("could get model permissions")
    }
  }

  private[this] def onSetModelPermissionsRequest(request: SetModelPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val SetModelPermissionsRequestMessage(modelId, overridePermissions, world, setAllUsers, addedUsers, removedUsers) = request
    val mappedWorld = world map (w => ModelPermissions(w.read, w.write, w.remove, w.manage))
    val mappedAddedUsers = addedUsers.mapValues(p => ModelPermissions(p.read, p.write, p.remove, p.manage))

    val message = SetModelPermissionsRequest(
      domainFqn,
      modelId,
      sessionKey,
      Some(overridePermissions),
      mappedWorld,
      setAllUsers,
      mappedAddedUsers,
      removedUsers.toList)
    val future = modelClusterRegion ? message
    future onComplete {
      case Success(_) =>
        cb.reply(SetModelPermissionsResponseMessage())
      case Failure(ModelNotFoundException(_)) =>
        cb.reply(ModelClientActor.ModelNotFoundError)
      case Failure(UnauthorizedException(message)) =>
        cb.reply(ErrorMessages.Unauthorized(message))
      case Failure(cause) =>
        log.error(cause, s"${domainFqn}: Unexpected error setting permissions for model.")
        cb.unexpectedError("Unexpected error setting permissions for model.")
    }
  }

  def resourceId(modelId: String): Option[String] = {
    this.modelIdToResourceId.get(modelId) orElse {
      log.error(s"${domainFqn}: Recevie an outgoing message for a modelId that is not open: ${modelId}")
      None
    }
  }

  def nextResourceId(): String = {
    val id = nextResourceId.toString();
    nextResourceId += 1;
    return id;
  }
}
