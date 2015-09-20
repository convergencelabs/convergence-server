package com.convergencelabs.server.frontend.realtime

import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.language.postfixOps
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest
import com.convergencelabs.server.frontend.realtime.proto.OpenRealtimeModelRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.ProtocolMessage
import akka.actor.ActorRef
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout
import scala.util.Success
import com.convergencelabs.server.domain.model.OpenModelResponse
import com.convergencelabs.server.frontend.realtime.proto.OpenRealtimeModelResponseMessage
import scala.util.Failure
import com.convergencelabs.server.frontend.realtime.proto.CloseRealtimeModelRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.OperationSubmissionMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelMessage
import com.convergencelabs.server.frontend.realtime.proto.IncomingModelRequestMessage
import com.convergencelabs.server.frontend.realtime.proto.OutgoingProtocolResponseMessage

class ModelClient(
    clientActor: ActorRef,
    modelManager: ActorRef,
    implicit val ec: ExecutionContext,
    connection: ProtocolConnection) {

  var openRealtimeModels = Map[String, ActorRef]()

  // FIXME hardcoded
  implicit val timeout = Timeout(5 seconds)

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

  def onOperationSubmission(opSubmission: OperationSubmissionMessage): Unit = {
    val modelActor = openRealtimeModels(opSubmission.resourceId)
    // somehow convert to a com.convergencelabs.server.domain.model.OperationSubmission
    val converted: String ="" // hack just to show what we will do
    modelActor ! converted
  }
  
  def onCloseRealtimeModelRequest(request: CloseRealtimeModelRequestMessage, reply: Promise[OutgoingProtocolResponseMessage]): Unit = {

  }

  def onOpenRealtimeModelRequest(request: OpenRealtimeModelRequestMessage, reply: Promise[OutgoingProtocolResponseMessage]): Unit = {
    val req = OpenRealtimeModelRequest(request.modelFqn, clientActor)

    val f = modelManager ? req
    // Validate what happens if the wrong type is returned.
    f.mapTo[OpenModelResponse] onComplete {
      case Success(OpenModelResponse(realtimeModelActor, modelResourceId, modelSessionId, metaData, modelData)) => {
        openRealtimeModels += (modelResourceId -> realtimeModelActor)
        val outgoing = OpenRealtimeModelResponseMessage(modelResourceId, modelSessionId, metaData, modelData)
        reply.success(outgoing)
      }
      case Failure(cause) => reply.failure(cause)
    }
  }
}