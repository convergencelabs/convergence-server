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
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest
import com.convergencelabs.server.domain.model.OpenModelResponse
import com.convergencelabs.server.domain.model.CloseRealtimeModelSuccess
import com.convergencelabs.server.domain.model.CloseRealtimeModelRequest
import com.convergencelabs.server.domain.model.OperationSubmission
import com.convergencelabs.server.domain.model.OutgoingOperation
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


class ModelClient(
    clientActor: ActorRef,
    modelManager: ActorRef,
    implicit val ec: ExecutionContext,
    connection: ProtocolConnection) {

  implicit val sender = clientActor

  var openRealtimeModels = Map[String, ActorRef]()

  // FIXME hardcoded
  implicit val timeout = Timeout(5 seconds)

  def onOutgoingEvent(event: AnyRef): Unit = {
    event match {
      case op: OutgoingOperation => onOutgoingOperation(op)
    }
  }

  def onOutgoingOperation(op: OutgoingOperation): Unit = {
    var OutgoingOperation(resoruceId, clientId, contextVersion, timestamp, operation) = op
    
    connection.send(RemoteOperationMessage(
      resoruceId,
      clientId,
      contextVersion,
      timestamp,
      OperationMapper.mapOutgoing(operation)))
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
    val future = modelManager ? OpenRealtimeModelRequest(request.modelFqn, clientActor)
    future.mapReponse[OpenModelResponse]  onComplete {
      case Success(OpenModelResponse(realtimeModelActor, modelResourceId, modelSessionId, metaData, modelData)) => {
        openRealtimeModels += (modelResourceId -> realtimeModelActor)
        reply.success(
          OpenRealtimeModelResponseMessage(modelResourceId, modelSessionId, metaData, modelData))
      }
      case Failure(cause) => reply.failure(cause)
    }
  }
}