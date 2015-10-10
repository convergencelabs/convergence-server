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
  def props(modelManager: ActorRef): Props =
    Props(new ModelClientActor(modelManager))
}

class ModelClientActor(
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
    val OutgoingOperation(resoruceId, clientId, contextVersion, timestamp, operation) = op

    context.parent ! RemoteOperationMessage(
      resoruceId,
      clientId,
      contextVersion,
      timestamp,
      OperationMapper.mapOutgoing(operation))
  }

  def onOperationAcknowledgement(opAck: OperationAcknowledgement): Unit = {
    val OperationAcknowledgement(resourceId, clientId, version) = opAck
    context.parent ! OperationAcknowledgementMessage(resourceId, clientId, version)
  }

  def onRemoteClientOpened(opened: RemoteClientOpened): Unit = {
    var RemoteClientOpened(resourceId, clientId) = opened
    context.parent ! RemoteClientOpenedMessage(resourceId, clientId)
  }

  def onRemoteClientClosed(closed: RemoteClientClosed): Unit = {
    var RemoteClientClosed(resourceId, clientId) = closed
    context.parent ! RemoteClientClosedMessage(resourceId, clientId)
  }

  def onModelForceClose(forceClose: ModelForceClose): Unit = {
    var ModelForceClose(resourceId, clientId, reason) = forceClose
    context.parent ! ModelForceCloseMessage(resourceId, clientId, reason)
  }

  def onClientModelDataRequest(dataRequest: ClientModelDataRequest): Unit = {
    val ClientModelDataRequest(modelFqn: ModelFqn) = dataRequest
    val askingActor = sender()
    val future = context.parent ? ModelDataRequestMessage(modelFqn)
    future.mapReponse[ModelDataResponseMessage] onComplete {
      case Success(ModelDataResponseMessage(data)) => {
        askingActor ! ClientModelDataResponse(data)
      }
      case Failure(cause) => // FIXME Send Error back
    }
  }

  //
  // Incoming Messages
  //

  def onRequestReceived(message: IncomingModelRequestMessage, replyPromise: Promise[OutgoingProtocolResponseMessage]): Unit = {
    message match {
      case request: OpenRealtimeModelRequestMessage => onOpenRealtimeModelRequest(request, replyPromise)
      case request: CloseRealtimeModelRequestMessage => onCloseRealtimeModelRequest(request, replyPromise)
    }
  }

  def onMessageReceived(message: IncomingProtocolNormalMessage): Unit = {
    message match {
      case submission: OperationSubmissionMessage => onOperationSubmission(submission)
    }
  }

  def onOperationSubmission(message: OperationSubmissionMessage): Unit = {
    val OperationSubmissionMessage(resourceId, clientId, version, operation) = message
    val submission = OperationSubmission(clientId, version, OperationMapper.mapIncoming(operation))
    val modelActor = openRealtimeModels(resourceId)
    modelActor ! submission
  }

  def onCloseRealtimeModelRequest(request: CloseRealtimeModelRequestMessage, reply: Promise[OutgoingProtocolResponseMessage]): Unit = {
    openRealtimeModels.get(request.rId) match {
      case Some(modelActor) => {
        val future = modelActor ? CloseRealtimeModelRequest(request.cId)
        future.mapReponse[CloseRealtimeModelSuccess] onComplete {
          case Success(CloseRealtimeModelSuccess()) => {
            openRealtimeModels -= request.rId
            reply.success(CloseRealtimeModelResponseMessage())
          }
          case Failure(cause) => reply.failure(cause)
        }
      }
      case None => reply.failure(new ErrorException("model_not_opened", "The requested model was not opened"))
    }
  }

  def onOpenRealtimeModelRequest(request: OpenRealtimeModelRequestMessage, reply: Promise[OutgoingProtocolResponseMessage]): Unit = {
    val future = modelManager ? OpenRealtimeModelRequest(request.modelFqn, self)
    future.mapReponse[OpenModelResponse] onComplete {
      case Success(OpenModelResponse(realtimeModelActor, modelResourceId, modelSessionId, metaData, modelData)) => {
        openRealtimeModels += (modelResourceId -> realtimeModelActor)
        reply.success(
          OpenRealtimeModelResponseMessage(modelResourceId, modelSessionId, metaData, modelData))
      }
      case Failure(cause) => reply.failure(cause)
    }
  }
}