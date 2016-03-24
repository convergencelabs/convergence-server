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
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import org.json4s.JsonAST.JInt
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.NullValue

class RealTimeModel(
    private[this] val fqn: ModelFqn,
    private[this] val resourceId: String,
    private[this] val cc: ServerConcurrencyControl,
    private val obj: ObjectValue) {

  val idToValue = collection.mutable.HashMap[String, RealTimeValue]()

  val data = this.createValue(obj, None, None)

  def contextVersion(): Long = {
    this.cc.contextVersion
  }

  def clientConnected(sk: String, contextVersion: Long): Unit = {
    this.cc.trackClient(sk, contextVersion)
  }

  def clientDisconnected(sk: String): Unit = {
    this.cc.untrackClient(sk)
  }

  def registerValue(realTimeValue: RealTimeValue): Unit = {
    this.idToValue += (realTimeValue.id -> realTimeValue)
  }

  def unregisterValue(realTimeValue: RealTimeValue): Unit = {
    this.idToValue -= realTimeValue.id
  }

  def createValue(
    value: DataValue,
    parent: Option[RealTimeContainerValue],
    parentField: Option[Any]): RealTimeValue = {
    value match {
      case v: StringValue => new RealTimeString(v, this, parent, parentField)
      case v: DoubleValue => new RealTimeDouble(v, this, parent, parentField)
      case v: BooleanValue => new RealTimeBoolean(v, this, parent, parentField)
      case v: ObjectValue => new RealTimeObject(v, this, parent, parentField)
      case v: ArrayValue => new RealTimeArray(v, this, parent, parentField)
      case v: NullValue => new RealTimeNull(v, this, parent, parentField)
      case _ => throw new IllegalArgumentException("Unsupported type: " + value)
    }
  }

  def processOperationEvent(unprocessed: UnprocessedOperationEvent): Try[ProcessedOperationEvent] = {
    val preprocessed = unprocessed.copy(operation = noOpObsoleteOperations(unprocessed.operation))
    val processed = cc.processRemoteOperation(preprocessed)
    applyOpperation(processed.operation) match {
      case Success(_) =>
        cc.commit()
        Success(processed)
      case Failure(f) =>
        cc.rollback()
        Failure(f)
    }
  }

  private[this] def noOpObsoleteOperations(op: Operation): Operation = {
    op match {
      case c: CompoundOperation =>
        val ops = c.operations map {
          o => noOpObsoleteOperations(o).asInstanceOf[DiscreteOperation]
        }
        c.copy(operations = ops)
      case d: DiscreteOperation =>
        if (this.idToValue.contains(d.id)) {
          d
        } else {
          d.clone(noOp = true)
        }
    }
  }

  private[this] def applyOpperation(op: Operation): Try[Unit] = {
    op match {
      case c: CompoundOperation =>
        c.operations foreach { o =>
          applyDiscreteOperation(o) match {
            case Failure(f) => throw f
            case _ =>
          }
        }
        Success(())
      case d: DiscreteOperation =>
        applyDiscreteOperation(d)
    }
  }

  def processReferenceEvent(event: ModelReferenceEvent, sk: String): Try[Option[RemoteReferenceEvent]] = Try {
    idToValue.get(event.id) match {
      case Some(realTimeValue) =>
        event match {
          case publish: PublishReference => 
            realTimeValue.processReferenceEvent(publish)
            val PublishReference(id, key, refType) = publish
            Some(RemoteReferencePublished(resourceId, sk, id, key, refType))
          case unpublish: UnpublishReference => 
            realTimeValue.processReferenceEvent(unpublish)
            val UnpublishReference(id, key) = unpublish
            Some(RemoteReferenceUnpublished(resourceId, sk, id, key))
          case set: SetReference => 
            val xformed = this.cc.processRemoteReferenceSet(sk, set)
            realTimeValue.processReferenceEvent(xformed)
            val SetReference(id, key, refType, value, versio) = xformed
            Some(RemoteReferenceSet(this.resourceId, sk, id, key, refType, value))
          case cleared: ClearReference => 
            realTimeValue.processReferenceEvent(cleared)
            val ClearReference(id, key) = cleared
            Some(RemoteReferenceCleared(resourceId, sk, id, key))
        }
      case None =>
        None
    }

  }

  def applyDiscreteOperation(op: DiscreteOperation): Try[Unit] = {
    if (!op.noOp) {
      val value = this.idToValue(op.id)
      value.processOperation(op)
    } else {
      Success(())
    }
  }
}