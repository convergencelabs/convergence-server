package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
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
import com.convergencelabs.server.domain.model.ModelForceClose
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelNotFound
import com.convergencelabs.server.domain.model.OpenModelResponse
import com.convergencelabs.server.domain.model.OpenModelSuccess
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest
import com.convergencelabs.server.domain.model.OperationAcknowledgement
import com.convergencelabs.server.domain.model.OperationSubmission
import com.convergencelabs.server.domain.model.OutgoingOperation
import com.convergencelabs.server.domain.model.RealtimeModelClientMessage
import com.convergencelabs.server.domain.model.RemoteClientClosed
import com.convergencelabs.server.domain.model.RemoteClientOpened
import com.convergencelabs.server.util.concurrent.AskFuture
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.model.ModelDeletedWhileOpening
import com.convergencelabs.server.domain.model.ClientDataRequestFailure

object ModelClientActor {
  def props(
    userId: String,
    sessionId: String,
    modelManager: ActorRef): Props =
    Props(new ModelClientActor(userId, sessionId, modelManager))
}

class ModelClientActor(
  userId: String,
  sessionId: String,
  modelManager: ActorRef)
    extends Actor with ActorLogging {

  var openRealtimeModels = Map[String, ActorRef]()

  // FIXME hardcoded
  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    case MessageReceived(message) if message.isInstanceOf[IncomingProtocolNormalMessage] =>
      onMessageReceived(message.asInstanceOf[IncomingProtocolNormalMessage])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[IncomingModelRequestMessage] =>
      onRequestReceived(message.asInstanceOf[IncomingModelRequestMessage], replyPromise)
    case message: RealtimeModelClientMessage =>
      onOutgoingModelMessage(message)
    case x: Any => unhandled(x)
  }

  //
  // Outgoing Messages
  //
  def onOutgoingModelMessage(event: RealtimeModelClientMessage): Unit = {
    event match {
      case op: OutgoingOperation => onOutgoingOperation(op)
      case opAck: OperationAcknowledgement => onOperationAcknowledgement(opAck)
      case remoteOpened: RemoteClientOpened => onRemoteClientOpened(remoteOpened)
      case remoteClosed: RemoteClientClosed => onRemoteClientClosed(remoteClosed)
      case foreceClosed: ModelForceClose => onModelForceClose(foreceClosed)
      case dataRequest: ClientModelDataRequest => onClientModelDataRequest(dataRequest)
    }
  }

  def onOutgoingOperation(op: OutgoingOperation): Unit = {
    val OutgoingOperation(resoruceId, userId, sessionId, contextVersion, timestamp, operation) = op

    context.parent ! RemoteOperationMessage(
      resoruceId,
      userId,
      sessionId,
      contextVersion,
      timestamp,
      OperationMapper.mapOutgoing(operation))
  }

  def onOperationAcknowledgement(opAck: OperationAcknowledgement): Unit = {
    val OperationAcknowledgement(resourceId, seqNo, version) = opAck
    context.parent ! OperationAcknowledgementMessage(resourceId, seqNo, version)
  }

  def onRemoteClientOpened(opened: RemoteClientOpened): Unit = {
    val RemoteClientOpened(resourceId, userId, sessionId) = opened
    context.parent ! RemoteClientOpenedMessage(resourceId, userId, sessionId)
  }

  def onRemoteClientClosed(closed: RemoteClientClosed): Unit = {
    val RemoteClientClosed(resourceId, userId, sessionId) = closed
    context.parent ! RemoteClientClosedMessage(resourceId, userId, sessionId)
  }

  def onModelForceClose(forceClose: ModelForceClose): Unit = {
    val ModelForceClose(resourceId, reason) = forceClose
    context.parent ! ModelForceCloseMessage(resourceId, reason)
  }

  def onClientModelDataRequest(dataRequest: ClientModelDataRequest): Unit = {
    val ClientModelDataRequest(ModelFqn(collectionId, modelId)) = dataRequest
    val askingActor = sender
    val future = context.parent ? ModelDataRequestMessage(ModelFqnData(collectionId, modelId))
    future.mapResponse[ModelDataResponseMessage] onComplete {
      case Success(ModelDataResponseMessage(data)) => {
        askingActor ! ClientModelDataResponse(data)
      }
      case Failure(cause) => {
        ??? // FIXME what to do?
      }
    }
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

  def onMessageReceived(message: IncomingProtocolNormalMessage): Unit = {
    message match {
      case submission: OperationSubmissionMessage => onOperationSubmission(submission)
    }
  }

  def onOperationSubmission(message: OperationSubmissionMessage): Unit = {
    val OperationSubmissionMessage(resourceId, seqNo, version, operation) = message
    val submission = OperationSubmission(seqNo, version, OperationMapper.mapIncoming(operation))
    val modelActor = openRealtimeModels(resourceId)
    modelActor ! submission
  }

  def onOpenRealtimeModelRequest(request: OpenRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val ModelFqnData(collectionId, modelId) = request.fqn
    val future = modelManager ? OpenRealtimeModelRequest(userId, sessionId, ModelFqn(collectionId, modelId), self)
    future.mapResponse[OpenModelResponse] onComplete {
      case Success(OpenModelSuccess(realtimeModelActor, modelResourceId, metaData, modelData)) => {
        openRealtimeModels += (modelResourceId -> realtimeModelActor)
        cb.reply(
          OpenRealtimeModelResponseMessage(
            modelResourceId,
            metaData.version,
            metaData.createdTime.toEpochMilli,
            metaData.modifiedTime.toEpochMilli,
            modelData))
      }
      case Success(ModelAlreadyOpen) => {
        cb.reply(ErrorMessage("model_already_open", "The requested model is already open by this client."))
      }
      case Success(ModelDeletedWhileOpening) => {
        cb.reply(ErrorMessage("model_deleted", "The requested model was deleted while opening."))
      }
      case Success(ClientDataRequestFailure(message)) => {
        cb.reply(ErrorMessage("data_request-Failure", message))
      }
      case Failure(cause) => {
        cb.unknownError()
      }
    }
  }

  def onCloseRealtimeModelRequest(request: CloseRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    openRealtimeModels.get(request.rId) match {
      case Some(modelActor) =>
        val future = modelActor ? CloseRealtimeModelRequest(userId, sessionId)
        future.mapResponse[CloseRealtimeModelSuccess] onComplete {
          case Success(CloseRealtimeModelSuccess()) =>
            openRealtimeModels -= request.rId
            cb.reply(SuccessMessage())
          case Failure(cause) =>
            cb.unexpectedError("could not close model")
        }
      case None =>
        cb.reply(ErrorMessage("model_not_open", s"the requested model was not open"))
    }
  }

  def onCreateRealtimeModelRequest(request: CreateRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val CreateRealtimeModelRequestMessage(ModelFqnData(collectionId, modelId), data) = request
    val future = modelManager ? CreateModelRequest(ModelFqn(collectionId, modelId), data)
    future.mapResponse[CreateModelResponse] onComplete {
      case Success(ModelCreated) => cb.reply(SuccessMessage())
      case Success(ModelAlreadyExists) => cb.reply(ErrorMessage("model_alread_exists", "A model with the specifieid collection and model id already exists"))
      case Failure(cause) => cb.unexpectedError("could not create model")
    }
  }

  def onDeleteRealtimeModelRequest(request: DeleteRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val DeleteRealtimeModelRequestMessage(ModelFqnData(collectionId, modelId)) = request
    val future = modelManager ? DeleteModelRequest(ModelFqn(collectionId, modelId))
    future.mapResponse[DeleteModelResponse] onComplete {
      case Success(ModelDeleted) => cb.reply(SuccessMessage())
      case Success(ModelNotFound) => cb.reply(ErrorMessage("model_not_found", "A model with the specifieid collection and model id does not exists"))
      case Failure(cause) => cb.unexpectedError("could not delete model")
    }
  }
}
