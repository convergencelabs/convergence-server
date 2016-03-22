package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JBool
import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JNull
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.Operation
import com.convergencelabs.server.domain.model.ot.ServerConcurrencyControl
import com.convergencelabs.server.domain.model.ot.UnprocessedOperationEvent
import com.convergencelabs.server.domain.model.ot.ProcessedOperationEvent
import java.time.Instant
import com.convergencelabs.server.datastore.domain.ModelOperationProcessor
import scala.util.Success
import scala.collection.immutable.HashMap
import org.json4s.JsonAST.JInt

class RealTimeModel(
    private[this] val fqn: ModelFqn,
    private[this] val cc: ServerConcurrencyControl,
    private val obj: JObject) {
  
  val data = this.createValue(None, None, obj)

  def contextVersion(): Long = {
    this.cc.contextVersion
  }
  
  def clientConnected(sk: String, contextVersion: Long): Unit = {
    this.cc.trackClient(sk, contextVersion)
  }

  def clientDisconnected(sk: String): Unit = {
    this.cc.untrackClient(sk)
  }

  def createValue(
    parent: Option[RealTimeContainerValue],
    parentField: Option[Any],
    value: JValue): RealTimeValue = {
    value match {
      case v: JString => new RealTimeString(this, parent, parentField, v)
      case v: JDouble => new RealTimeDouble(this, parent, parentField, v)
      case v: JInt => new RealTimeDouble(this, parent, parentField, JDouble(v.values.toDouble))
      case v: JBool => new RealTimeBoolean(this, parent, parentField, v)
      case v: JObject => new RealTimeObject(this, parent, parentField, v)
      case v: JArray => new RealTimeArray(this, parent, parentField, v)
      case JNull => new RealTimeNull(this, parent, parentField)
      case _ => throw new IllegalArgumentException("Unsupported type: " + value)
    }
  }

  def processOperationEvent(unprocessedOpEvent: UnprocessedOperationEvent): Try[ProcessedOperationEvent] = {
    val processedOpEvent = cc.processRemoteOperation(unprocessedOpEvent)
    applyOpperation(processedOpEvent.operation) match {
      case Success(_) =>
        cc.commit()
        Success(processedOpEvent)
      case Failure(f) =>
        cc.rollback()
        Failure(f)
    }
  }

  private[this] def applyOpperation(op: Operation): Try[Unit] = {
    op match {
      case c: CompoundOperation =>
        c.operations foreach { o =>
          applyOperation(o) match {
            case Failure(f) => throw f
            case _ =>
          }
        }
        Success(())
      case d: DiscreteOperation =>
        applyOperation(d)
    }
  }

  def processReferenceEvent(event: ModelReferenceEvent): Try[Unit] = {
    event match {
      case publishReference: PublishReference => onPublishReference(publishReference)
      case unpublishReference: UnpublishReference => onUnpublishReference(unpublishReference)
      case setReference: SetReference => onSetReference(setReference)
      case clearReference: ClearReference => onClearReference(clearReference)
    }
  }

  private[this] def onPublishReference(request: PublishReference): Try[Unit] = {
    Success(())
  }

  private[this] def onUnpublishReference(request: UnpublishReference): Try[Unit] = {
    Success(())
  }

  private[this] def onSetReference(request: SetReference): Try[Unit] = {
    Success(())
  }

  private[this] def onClearReference(request: ClearReference): Try[Unit] = {
    Success(())
  }

  def applyOperation(op: DiscreteOperation): Try[Unit] = {
    if (!op.noOp) {
      this.data.processOperation(op, op.path)
    } else {
      Success(())
    }
  }
}