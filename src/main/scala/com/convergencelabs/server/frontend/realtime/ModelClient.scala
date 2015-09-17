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
import com.convergencelabs.server.frontend.realtime.proto.ModelMessage
import com.convergencelabs.server.frontend.realtime.proto.OperationSubmission

class ModelClient(
    clientActor: ActorRef,
    modelManager: ActorRef,
    implicit val ec: ExecutionContext,
    connection: ProtocolConnection) extends AbstractProtocolMessageHandler(clientActor) {

  var openRealtimeModels = Map[String, ActorRef]()

  // FIXME hardcoded
  implicit val timeout = Timeout(5 seconds)

  def onRequestReceived(event: RequestReceived): Unit = {
    val message = event.message
    message match {
      case request: OpenRealtimeModelRequestMessage => onOpenRealtimeModelRequest(request, event.replyPromise)
      case request: CloseRealtimeModelRequestMessage => onCloseRealtimeModelRequest(request, event.replyPromise)
    }
  }

  def onMessageReceived(event: MessageReceived): Unit = {
    val message = event.message
    message match {
      case submission: OperationSubmission => onOperationSubmission(submission)
    }
  }

  def onOperationSubmission(opSubmission: OperationSubmission): Unit = {
    val modelActor = openRealtimeModels(opSubmission.resourceId)
    // somehow convert to a com.convergencelabs.server.domain.model.OperationSubmission
    val converted: String ="" // hack just to show what we will do
    modelActor ! converted
  }
  
  def onCloseRealtimeModelRequest(request: CloseRealtimeModelRequestMessage, reply: Promise[ProtocolMessage]): Unit = {

  }

  def onOpenRealtimeModelRequest(request: OpenRealtimeModelRequestMessage, reply: Promise[ProtocolMessage]): Unit = {
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