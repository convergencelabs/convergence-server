package com.convergencelabs.server.frontend.realtime

import akka.actor.ActorRef
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.language.postfixOps
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest
import com.convergencelabs.server.domain.model.OpenModelResponse
import com.convergencelabs.server.domain.model.CloseRealtimeModelSuccess
import com.convergencelabs.server.domain.model.CloseRealtimeModelRequest
import com.convergencelabs.server.domain.model.OperationSubmission
import com.convergencelabs.server.domain.model.Error
import com.convergencelabs.server.domain.model.ot.ops._
import com.convergencelabs.server.frontend.realtime.proto._

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

  def onOperationSubmission(message: OperationSubmissionMessage): Unit = {
    val OperationSubmissionMessage(resourceId, clientId, version, operation) = message
    val submission = OperationSubmission(clientId, version, OperationMapper.mapIncoming(operation))
    val modelActor = openRealtimeModels(resourceId)
    modelActor ! submission
  }

  def onCloseRealtimeModelRequest(request: CloseRealtimeModelRequestMessage, reply: Promise[OutgoingProtocolResponseMessage]): Unit = {
    // FIXME validate model open?
    val modelActor = openRealtimeModels(request.rId)
    val req = CloseRealtimeModelRequest(request.cId)
    openRealtimeModels -= request.rId

    val f = modelActor ? req
    // Validate what happens if the wrong type is returned.
    f onComplete {
      case Success(CloseRealtimeModelSuccess()) => {
        reply.success(CloseRealtimeModelResponseMessage())
      }
      case Success(Error(code, reason)) => reply.failure(new ErrorException(code, reason))
      case Success(_) => reply.failure(new ErrorException())
      case Failure(cause) => reply.failure(cause)
    }
  }

  def onOpenRealtimeModelRequest(request: OpenRealtimeModelRequestMessage, reply: Promise[OutgoingProtocolResponseMessage]): Unit = {
    val req = OpenRealtimeModelRequest(request.modelFqn, clientActor)

    val f = modelManager ? req
    // Validate what happens if the wrong type is returned.
    f onComplete {
      case Success(OpenModelResponse(realtimeModelActor, modelResourceId, modelSessionId, metaData, modelData)) => {
        openRealtimeModels += (modelResourceId -> realtimeModelActor)
        reply.success(
          OpenRealtimeModelResponseMessage(modelResourceId, modelSessionId, metaData, modelData))
      }
      case Success(Error(code, reason)) => reply.failure(new ErrorException(code, reason))
      case Success(_) => reply.failure(new ErrorException())
      case Failure(cause) => reply.failure(cause)
    }
  }
}

object OperationMapper {

  def mapIncoming(op: OperationData): Operation = {
    op match {
      case operation: CompoundOperationData => mapIncomgin(operation)
      case operation: DiscreteOperationData => mapIncoming(operation)
    }
  }
  
  def mapIncomgin(op: CompoundOperationData): CompoundOperation = {
    CompoundOperation(op.ops.map(opData => mapIncoming(opData).asInstanceOf[DiscreteOperation]))
  }
  
  def mapIncomgin(op: DiscreteOperationData): DiscreteOperation = {
    op match {
      case StringInsertOperationData(path, noOp, index, value) => StringInsertOperation(path, noOp, index, value)
      case StringRemoveOperationData(path, noOp, index, value) => StringDeleteOperation(path, noOp, index, value)
      case StringSetOperationData(path, noOp,  value) => StringSetOperation(path, noOp, value)
      
      case ArrayInsertOperationData(path, noOp, idx, newVal) => ArrayInsertOperation(path, noOp, idx, newVal)
      case ArrayRemoveOperationData(path, noOp, idx) => ArrayRemoveOperation(path, noOp, idx)
      case ArrayMoveOperationData(path, noOp, fromIdx, toIdx) => ArrayMoveOperation(path, noOp, fromIdx, toIdx)
      case ArrayReplaceOperationData(path, noOp, idx, newVal) => ArrayReplaceOperation(path, noOp, idx, newVal)
      case ArraySetOperationData(path, noOp, array) => ArraySetOperation(path, noOp, array)
      
      case ObjectSetPropertyOperationData(path, noOp, prop, newVal) => ObjectSetPropertyOperation(path, noOp, prop, newVal)
      case ObjectAddPropertyOperationData(path, noOp, prop, newVal) => ObjectAddPropertyOperation(path, noOp, prop, newVal)
      case ObjectRemovePropertyOperationData(path, noOp, prop) => ObjectRemovePropertyOperation(path, noOp, prop)
      case ObjectSetOperationData(path, noOp, objectData) => ObjectSetOperation(path, noOp, objectData)
      
      case NumberAddOperationData(path, noOp, delta) => NumberAddOperation(path, noOp, delta)
      case NumberSetOperationData(path, noOp, number) => NumberSetOperation(path, noOp, number)
    }
  }
  
}