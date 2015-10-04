package com.convergencelabs.server.frontend.realtime

import akka.actor.ActorRef
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import com.convergencelabs.server.domain.model._
import com.convergencelabs.server.frontend.realtime.proto.RemoteOperationMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.OpenRealtimeModelRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.CloseRealtimeModelRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelMessage
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.OperationSubmissionMessage
import com.convergencelabs.server.frontend.realtime.proto.OperationSubmissionMessage
import com.convergencelabs.server.frontend.realtime.proto.OperationSubmissionMessage
import com.convergencelabs.server.frontend.realtime.proto.CloseRealtimeModelResponseMessage
import com.convergencelabs.server.frontend.realtime.proto.OpenRealtimeModelResponseMessage
import com.convergencelabs.server.util.concurrent._
import com.convergencelabs.server.domain.model.OperationAcknowledgement
import com.convergencelabs.server.frontend.realtime.proto.OperationAcknowledgementMessage
import com.convergencelabs.server.frontend.realtime.proto.RemoteClientOpenedMessage
import com.convergencelabs.server.frontend.realtime.proto.ModelForceCloseMessage
import com.convergencelabs.server.frontend.realtime.proto.RemoteClientClosedMessage
import com.convergencelabs.server.frontend.realtime.proto.ModelDataRequestMessage


class ModelClient(
    clientActor: ClientActor,
    modelManager: ActorRef,
    implicit val ec: ExecutionContext) {

  implicit val sender = clientActor

  var openRealtimeModels = Map[String, ActorRef]()

  // FIXME hardcoded
  implicit val timeout = Timeout(5 seconds)

  def onOutgoingModelMessage(event: RealtimeModelClientMessage, sender: ActorRef): Unit = {
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
    var OutgoingOperation(resoruceId, clientId, contextVersion, timestamp, operation) = op
    clientActor.send(RemoteOperationMessage(
      resoruceId,
      clientId,
      contextVersion,
      timestamp,
      OperationMapper.mapOutgoing(operation)))
  }

  def onOperationAcknowledgement(opAck: OperationAcknowledgement): Unit = {
    val OperationAcknowledgement(resourceId, clientId, version) = opAck
    clientActor.send(OperationAcknowledgementMessage(resourceId, clientId, version))
  }

  def onRemoteClientOpened(opened: RemoteClientOpened): Unit = {
    var RemoteClientOpened(resourceId, clientId) = opened
    clientActor.send(RemoteClientOpenedMessage(resourceId, clientId))
  }

  def onRemoteClientClosed(closed: RemoteClientClosed): Unit = {
    var RemoteClientClosed(resourceId, clientId) = closed
    clientActor.send(RemoteClientClosedMessage(resourceId, clientId))
  }

  def onModelForceClose(forceClose: ModelForceClose): Unit = {
    var ModelForceClose(resourceId, clientId, reason) = forceClose
    clientActor.send(ModelForceCloseMessage(resourceId, clientId, reason))
  }

  def onClientModelDataRequest(dataRequest: ClientModelDataRequest): Unit = {
    var ClientModelDataRequest(modelFqn: ModelFqn) = dataRequest
    clientActor.send(ModelDataRequestMessage(modelFqn))
  }

  def onRequestReceived(message: IncomingModelRequestMessage, replyPromise: Promise[OutgoingProtocolResponseMessage]): Unit = {
    message match {
      case request: OpenRealtimeModelRequestMessage => onOpenRealtimeModelRequest(request, replyPromise)
      case request: CloseRealtimeModelRequestMessage => onCloseRealtimeModelRequest(request, replyPromise)
    }
  }

  def onMessageReceived(message: IncomingModelMessage): Unit = {
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
    val future = modelManager ? OpenRealtimeModelRequest(request.modelFqn, clientActor.self)
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