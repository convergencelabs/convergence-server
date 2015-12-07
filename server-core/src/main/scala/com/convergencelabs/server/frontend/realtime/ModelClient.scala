package com.convergencelabs.server.frontend.realtime

import akka.actor.ActorRef
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout
import akka.actor.Props
import akka.actor.ActorLogging
import akka.pattern._
import akka.actor.Actor
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import com.convergencelabs.server.domain.model._
import com.convergencelabs.server.frontend.realtime.proto._
import com.convergencelabs.server.frontend.realtime.proto.OpenRealtimeModelResponseMessage
import com.convergencelabs.server.util.concurrent._

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
    case x => unhandled(x)
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
        println(cause)
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
      case Failure(cause) => {
        cb.error(cause)
      }
    }
  }

  def onCloseRealtimeModelRequest(request: CloseRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    openRealtimeModels.get(request.rId) match {
      case Some(modelActor) => {
        val future = modelActor ? CloseRealtimeModelRequest(userId, sessionId)
        future.mapResponse[CloseRealtimeModelSuccess] onComplete {
          case Success(CloseRealtimeModelSuccess()) => {
            openRealtimeModels -= request.rId
            cb.reply(SuccessMessage())
          }
          case Failure(cause) => cb.error(cause)
        }
      }
      case None => cb.error(new UnexpectedErrorException("model_not_opened", "The requested model was not opened"))
    }
  }

  def onCreateRealtimeModelRequest(request: CreateRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val CreateRealtimeModelRequestMessage(ModelFqnData(collectionId, modelId), data) = request
    val future = modelManager ? CreateModelRequest(ModelFqn(collectionId, modelId), data)
    future.mapResponse[CreateModelResponse] onComplete {
      case Success(ModelCreated) => cb.reply(SuccessMessage())
      case Success(ModelAlreadyExists) => cb.reply(ErrorMessage("model_alread_exists", "A model with the specifieid collection and model id already exists"))
      case Failure(cause) => cb.error(cause)
    }
  }

  def onDeleteRealtimeModelRequest(request: DeleteRealtimeModelRequestMessage, cb: ReplyCallback): Unit = {
    val DeleteRealtimeModelRequestMessage(ModelFqnData(collectionId, modelId)) = request
    val future = modelManager ? DeleteModelRequest(ModelFqn(collectionId, modelId))
    future.mapResponse[DeleteModelResponse] onComplete {
      case Success(ModelDeleted) => cb.reply(SuccessMessage())
      case Success(ModelNotFound) => cb.reply(ErrorMessage("model_not_found", "A model with the specifieid collection and model id does not exists"))
      case Failure(cause) => cb.error(cause)
    }
  }
}