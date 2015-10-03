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
import com.convergencelabs.server.domain.model.OutgoingOperation

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
      case operation: CompoundOperationData => mapIncoming(operation)
      case operation: DiscreteOperationData => mapIncoming(operation)
    }
  }

  def mapIncoming(op: CompoundOperationData): CompoundOperation = {
    CompoundOperation(op.ops.map(opData => mapIncoming(opData).asInstanceOf[DiscreteOperation]))
  }

  def mapIncomgin(op: DiscreteOperationData): DiscreteOperation = {
    op match {
      case StringInsertOperationData(path, noOp, index, value) => StringInsertOperation(path, noOp, index, value)
      case StringRemoveOperationData(path, noOp, index, value) => StringDeleteOperation(path, noOp, index, value)
      case StringSetOperationData(path, noOp, value) => StringSetOperation(path, noOp, value)

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

  def mapOutgoing(op: Operation): OperationData = {
    op match {
      case operation: CompoundOperation => mapOutgoing(operation)
      case operation: DiscreteOperation => mapOutgoing(operation)
    }
  }

  def mapOutgoing(op: CompoundOperation): CompoundOperationData = {
    CompoundOperationData(op.operations.map(opData => mapOutgoing(opData).asInstanceOf[DiscreteOperationData]))
  }

  def mapOutgoing(op: DiscreteOperation): DiscreteOperationData = {
    op match {
      case StringInsertOperation(path, noOp, index, value) => StringInsertOperationData(path, noOp, index, value)
      case StringDeleteOperation(path, noOp, index, value) => StringRemoveOperationData(path, noOp, index, value)
      case StringSetOperation(path, noOp, value) => StringSetOperationData(path, noOp, value)

      case ArrayInsertOperation(path, noOp, idx, newVal) => ArrayInsertOperationData(path, noOp, idx, newVal)
      case ArrayRemoveOperation(path, noOp, idx) => ArrayRemoveOperationData(path, noOp, idx)
      case ArrayMoveOperation(path, noOp, fromIdx, toIdx) => ArrayMoveOperationData(path, noOp, fromIdx, toIdx)
      case ArrayReplaceOperation(path, noOp, idx, newVal) => ArrayReplaceOperationData(path, noOp, idx, newVal)
      case ArraySetOperation(path, noOp, array) => ArraySetOperationData(path, noOp, array)

      case ObjectSetPropertyOperation(path, noOp, prop, newVal) => ObjectSetPropertyOperationData(path, noOp, prop, newVal)
      case ObjectAddPropertyOperation(path, noOp, prop, newVal) => ObjectAddPropertyOperationData(path, noOp, prop, newVal)
      case ObjectRemovePropertyOperation(path, noOp, prop) => ObjectRemovePropertyOperationData(path, noOp, prop)
      case ObjectSetOperation(path, noOp, objectData) => ObjectSetOperationData(path, noOp, objectData)

      case NumberAddOperation(path, noOp, delta) => NumberAddOperationData(path, noOp, delta)
      case NumberSetOperation(path, noOp, number) => NumberSetOperationData(path, noOp, number)
    }
  }
}