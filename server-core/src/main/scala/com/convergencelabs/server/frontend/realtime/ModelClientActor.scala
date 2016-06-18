package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.domain.model.ClearReference
import com.convergencelabs.server.domain.model.ClientDataRequestFailure
import com.convergencelabs.server.domain.model.ClientModelDataRequest
import com.convergencelabs.server.domain.model.ClientModelDataResponse
import com.convergencelabs.server.domain.model.CloseRealtimeModelRequest
import com.convergencelabs.server.domain.model.CloseRealtimeModelSuccess
import com.convergencelabs.server.domain.model.CreateModelRequest
import com.convergencelabs.server.domain.model.CreateModelResponse
import com.convergencelabs.server.domain.model.DeleteModelRequest
import com.convergencelabs.server.domain.model.DeleteModelResponse
import com.convergencelabs.server.domain.model.ModelAlreadyExists
import com.convergencelabs.server.domain.model.ModelAlreadyOpen
import com.convergencelabs.server.domain.model.ModelCreated
import com.convergencelabs.server.domain.model.ModelDeleted
import com.convergencelabs.server.domain.model.ModelDeletedWhileOpening
import com.convergencelabs.server.domain.model.ModelForceClose
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelNotFound
import com.convergencelabs.server.domain.model.NoSuchModel
import com.convergencelabs.server.domain.model.OpenModelResponse
import com.convergencelabs.server.domain.model.OpenModelSuccess
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest
import com.convergencelabs.server.domain.model.OperationAcknowledgement
import com.convergencelabs.server.domain.model.OperationSubmission
import com.convergencelabs.server.domain.model.OutgoingOperation
import com.convergencelabs.server.domain.model.PublishReference
import com.convergencelabs.server.domain.model.RealtimeModelClientMessage
import com.convergencelabs.server.domain.model.ReferenceState
import com.convergencelabs.server.domain.model.ReferenceType
import com.convergencelabs.server.domain.model.RemoteClientClosed
import com.convergencelabs.server.domain.model.RemoteClientOpened
import com.convergencelabs.server.domain.model.RemoteReferenceCleared
import com.convergencelabs.server.domain.model.RemoteReferencePublished
import com.convergencelabs.server.domain.model.RemoteReferenceSet
import com.convergencelabs.server.domain.model.RemoteReferenceUnpublished
import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.model.SetReference
import com.convergencelabs.server.domain.model.UnpublishReference
import com.convergencelabs.server.util.concurrent.AskFuture

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout

object ModelClientActor {
  def props(
    sk: SessionKey,
    modelManager: ActorRef): Props =
    Props(new ModelClientActor(sk, modelManager))
}

class ModelClientActor(
  sessionKey: SessionKey,
  modelManager: ActorRef)
    extends Actor with ActorLogging {

  var openRealtimeModels = Map[String, ActorRef]()

  // FIXME hardcoded
  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    case MessageReceived(message) if message.isInstanceOf[IncomingModelNormalMessage] =>
      onMessageReceived(message.asInstanceOf[IncomingModelNormalMessage])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[IncomingModelRequestMessage] =>
      onRequestReceived(message.asInstanceOf[IncomingModelRequestMessage], replyPromise)
    case message: RealtimeModelClientMessage =>
      onOutgoingModelMessage(message)
    case x: Any => unhandled(x)
  }

  //
  // Outgoing Messages
  //
  // scalastyle:off cyclomatic.complexity
  def onOutgoingModelMessage(event: RealtimeModelClientMessage): Unit = {
    event match {
      case op: OutgoingOperation => onOutgoingOperation(op)
      case opAck: OperationAcknowledgement => onOperationAcknowledgement(opAck)
      case remoteOpened: RemoteClientOpened => onRemoteClientOpened(remoteOpened)
      case remoteClosed: RemoteClientClosed => onRemoteClientClosed(remoteClosed)
      case foreceClosed: ModelForceClose => onModelForceClose(foreceClosed)
      case dataRequest: ClientModelDataRequest => onClientModelDataRequest(dataRequest)
      case refPublished: RemoteReferencePublished => onRemoteReferencePublished(refPublished)
      case refUnpublished: RemoteReferenceUnpublished => onRemoteReferenceUnpublished(refUnpublished)
      case refSet: RemoteReferenceSet => onRemoteReferenceSet(refSet)
      case refCleared: RemoteReferenceCleared => onRemoteReferenceCleared(refCleared)
    }
  }
  // scalastyle:on cyclomatic.complexity

  def onOutgoingOperation(op: OutgoingOperation): Unit = {
    val OutgoingOperation(resoruceId, sessionKey, contextVersion, timestamp, operation) = op

    context.parent ! RemoteOperationMessage(
      resoruceId,
      sessionKey.serialize(),
      contextVersion,
      timestamp,
      OperationMapper.mapOutgoing(operation))
  }

  def onOperationAcknowledgement(opAck: OperationAcknowledgement): Unit = {
    val OperationAcknowledgement(resourceId, seqNo, version) = opAck
    context.parent ! OperationAcknowledgementMessage(resourceId, seqNo, version)
  }

  def onRemoteClientOpened(opened: RemoteClientOpened): Unit = {
    val RemoteClientOpened(resourceId, sk) = opened
    context.parent ! RemoteClientOpenedMessage(resourceId, sk.serialize())
  }

  def onRemoteClientClosed(closed: RemoteClientClosed): Unit = {
    val RemoteClientClosed(resourceId, sk) = closed
    context.parent ! RemoteClientClosedMessage(resourceId, sk.serialize())
  }

  def onModelForceClose(forceClose: ModelForceClose): Unit = {
    val ModelForceClose(resourceId, reason) = forceClose
    context.parent ! ModelForceCloseMessage(resourceId, reason)
  }

  def onClientModelDataRequest(dataRequest: ClientModelDataRequest): Unit = {
    val ClientModelDataRequest(ModelFqn(collectionId, modelId)) = dataRequest
    val askingActor = sender
    val future = context.parent ? ModelDataRequestMessage(collectionId, modelId)
    future.mapResponse[ModelDataResponseMessage] onComplete {
      case Success(ModelDataResponseMessage(data)) =>
        askingActor ! ClientModelDataResponse(data)
      case Failure(cause) =>
        // forward the failure to the asker, so we fail fast.
        askingActor ! akka.actor.Status.Failure(cause)
    }
  }

  def onRemoteReferencePublished(refPublished: RemoteReferencePublished): Unit = {
    val RemoteReferencePublished(resourceId, sessionId, path, key, refType) = refPublished
    context.parent ! RemoteReferencePublishedMessage(resourceId, sessionId, path, key, ReferenceType.map(refType))
  }

  def onRemoteReferenceUnpublished(refUnpublished: RemoteReferenceUnpublished): Unit = {
    val RemoteReferenceUnpublished(resourceId, sessionId, path, key) = refUnpublished
    context.parent ! RemoteReferenceUnpublishedMessage(resourceId, sessionId, path, key)
  }

  def onRemoteReferenceSet(refSet: RemoteReferenceSet): Unit = {
    val RemoteReferenceSet(resourceId, sessionId, path, key, refType, value) = refSet
    val mappedType = ReferenceType.map(refType)
    val mappedValue = mapOutgoingReferenceValue(refType, value)
    context.parent ! RemoteReferenceSetMessage(resourceId, sessionId, path, key, mappedType, mappedValue)
  }

  def mapOutgoingReferenceValue(refType: ReferenceType.Value, value: Any): Any = {
    refType match {
      case ReferenceType.Range =>
        val range = value.asInstanceOf[(Int, Int)]
        List(range._1, range._2)
      case _ =>
        value
    }
  }

  def onRemoteReferenceCleared(refCleared: RemoteReferenceCleared): Unit = {
    val RemoteReferenceCleared(resourceId, sessionId, path, key) = refCleared
    context.parent ! RemoteReferenceClearedMessage(resourceId, sessionId, path, key)
  }

  //
  // Incoming Messages
  //

  def onRequestReceived(message: IncomingModelRequestMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case openRequest: OpenRealtimeModelRequestMessage => onOpenRealtimeModelRequest(openRequest, replyCallback)
      case closeRequest: CloseRealtimeModelRequestMessage => onCloseRealtimeModelRequest(closeRequest, replyCallback)
      case createRequest: CreateRealtimeModelRequestMessage => onCreateRealtimeModelRequest(createRequest, replyCallback)
      case deleteRequest: DeleteRealtimeModelRequestMessage => onDeleteRealtimeModelRequest(deleteRequest, replyCallback)
    }
  }

  def onMessageReceived(message: IncomingModelNormalMessage): Unit = {
    message match {
      case submission: OperationSubmissionMessage => onOperationSubmission(submission)
      case publishReference: PublishReferenceMessage => onPublishReference(publishReference)
      case unpublishReference: UnpublishReferenceMessage => onUnpublishReference(unpublishReference)
      case setReference: SetReferenceMessage => onSetReference(setReference)
      case clearReference: ClearReferenceMessage => onClearReference(clearReference)
    }
  }

  def onOperationSubmission(message: OperationSubmissionMessage): Unit = {
    val OperationSubmissionMessage(resourceId, seqNo, version, operation) = message
    val submission = OperationSubmission(seqNo, version, OperationMapper.mapIncoming(operation))
    val modelActor = openRealtimeModels(resourceId)
    modelActor ! submission
  }

  def onPublishReference(message: PublishReferenceMessage): Unit = {
    val PublishReferenceMessage(resourceId, id, key, refType) = message
    val publishReference = PublishReference(id, key, ReferenceType.map(refType))
    val modelActor = openRealtimeModels(resourceId)
    modelActor ! publishReference
  }

  def onUnpublishReference(message: UnpublishReferenceMessage): Unit = {
    val UnpublishReferenceMessage(resourceId, id, key) = message
    val unpublishReference = UnpublishReference(id, key)
    val modelActor = openRealtimeModels(resourceId)
    modelActor ! unpublishReference
  }

  def onSetReference(message: SetReferenceMessage): Unit = {
    val SetReferenceMessage(resourceId, id, key, refType, value, version) = message
    val mappedType = ReferenceType.map(refType)
    val setReference = SetReference(id, key, mappedType, mapIncomingReferenceValue(mappedType, value), version)
    val modelActor = openRealtimeModels(resourceId)
    modelActor ! setReference
  }

  def mapIncomingReferenceValue(refType: ReferenceType.Value, value: Any): Any = {
    refType match {
      case ReferenceType.Index =>
        value.asInstanceOf[BigInt].intValue()
      case ReferenceType.Range =>
        val range = value.asInstanceOf[List[BigInt]]
        (range(0).intValue(), range(1).intValue())
      case _ =>
        value
    }
  }

  def onClearReference(message: ClearReferenceMessage): Unit = {
    val ClearReferenceMessage(resourceId, id, key) = message
    val clearReference = ClearReference(id, key)
    val modelActor = openRealtimeModels(resourceId)
    modelActor ! clearReference
  }

  def onOpenRealtimeModelRequest(request: OpenRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val future = modelManager ? OpenRealtimeModelRequest(
      sessionKey, ModelFqn(request.c, request.m), request.i, self)
    future.mapResponse[OpenModelResponse] onComplete {
      case Success(OpenModelSuccess(realtimeModelActor, modelResourceId, valueIdPrefix, metaData, connectedClients, references, modelData)) => {
        openRealtimeModels += (modelResourceId -> realtimeModelActor)
        val convertedReferences = references.map { ref =>
          val ReferenceState(sessionId, valueId, key, refType, value) = ref
          val mappedType = ReferenceType.map(refType)
          val mappedValue = value.map { v => mapOutgoingReferenceValue(refType, v) }
          ReferenceData(sessionId, valueId, key, mappedType, mappedValue)
        }.toSet
        cb.reply(
          OpenRealtimeModelResponseMessage(
            modelResourceId,
            valueIdPrefix,
            metaData.version,
            metaData.createdTime.toEpochMilli,
            metaData.modifiedTime.toEpochMilli,
            OpenModelData(
              modelData,
              connectedClients.map({ x => x.serialize() }),
              convertedReferences)))
      }
      case Success(ModelAlreadyOpen) => {
        cb.expectedError("model_already_open", "The requested model is already open by this client.")
      }
      case Success(NoSuchModel) => {
        cb.expectedError("no_such_model", "The requested model does not exists, and no initializer was provided.")
      }
      case Success(ModelDeletedWhileOpening) => {
        cb.expectedError("model_deleted", "The requested model was deleted while opening.")
      }
      case Success(ClientDataRequestFailure(message)) => {
        cb.expectedError("data_request_failure", message)
      }
      case Failure(cause) => {
        log.error(cause, "Unexpected error opening model.")
        cb.unknownError()
      }
    }
  }

  def onCloseRealtimeModelRequest(request: CloseRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    openRealtimeModels.get(request.r) match {
      case Some(modelActor) =>
        val future = modelActor ? CloseRealtimeModelRequest(sessionKey)
        future.mapResponse[CloseRealtimeModelSuccess] onComplete {
          case Success(CloseRealtimeModelSuccess()) =>
            openRealtimeModels -= request.r
            cb.reply(CloseRealTimeModelSuccessMessage())
          case Failure(cause) =>
            log.error(cause, "Unexpected error closing model.")
            cb.unexpectedError("could not close model")
        }
      case None =>
        cb.reply(ErrorMessage("model_not_open", s"the requested model was not open"))
    }
  }

  def onCreateRealtimeModelRequest(request: CreateRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val CreateRealtimeModelRequestMessage(collectionId, modelId, data) = request
    val future = modelManager ? CreateModelRequest(ModelFqn(collectionId, modelId), data)
    future.mapResponse[CreateModelResponse] onComplete {
      case Success(ModelCreated) => cb.reply(CreateRealtimeModelSuccessMessage())
      case Success(ModelAlreadyExists) => cb.expectedError("model_alread_exists", "A model with the specifieid collection and model id already exists")
      case Failure(cause) =>
        log.error(cause, "Unexpected error creating model.")
        cb.unexpectedError("could not create model")
    }
  }

  def onDeleteRealtimeModelRequest(request: DeleteRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val DeleteRealtimeModelRequestMessage(collectionId, modelId) = request
    val future = modelManager ? DeleteModelRequest(ModelFqn(collectionId, modelId))
    future.mapResponse[DeleteModelResponse] onComplete {
      case Success(ModelDeleted) => cb.reply(DeleteRealtimeModelSuccessMessage())
      case Success(ModelNotFound) => cb.reply(ErrorMessage("model_not_found", "A model with the specifieid collection and model id does not exists"))
      case Failure(cause) =>
        log.error(cause, "Unexpected error deleting model.")
        cb.unexpectedError("could not delete model")
    }
  }
}
