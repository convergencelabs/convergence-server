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
import com.convergencelabs.server.domain.model.reference.ModelReference
import com.convergencelabs.server.domain.model.reference.IndexReference
import com.convergencelabs.server.domain.model.reference.RangeReference
import com.convergencelabs.server.domain.model.reference.ModelReference

class RealTimeModel(
    private[this] val fqn: ModelFqn,
    private[this] val resourceId: String,
    private[this] val cc: ServerConcurrencyControl,
    private val obj: ObjectValue) {

  val idToValue = collection.mutable.HashMap[String, RealTimeValue]()

  val data = this.createValue(obj, None, None).asInstanceOf[RealTimeObject]

  def contextVersion(): Long = {
    this.cc.contextVersion
  }

  def clientConnected(sk: String, contextVersion: Long): Unit = {
    this.cc.trackClient(sk, contextVersion)
  }

  def clientDisconnected(sk: String): Unit = {
    this.cc.untrackClient(sk)
    this.data.sessionDisconnected(sk)
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
    // FIXME  We need to validate the operation (id != null for example)
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
            realTimeValue.processReferenceEvent(publish, sk)
            val PublishReference(id, key, refType) = publish
            Some(RemoteReferencePublished(resourceId, sk, id, key, refType))
          case unpublish: UnpublishReference =>
            realTimeValue.processReferenceEvent(unpublish, sk)
            val UnpublishReference(id, key) = unpublish
            Some(RemoteReferenceUnpublished(resourceId, sk, id, key))
          case set: SetReference =>
            this.cc.processRemoteReferenceSet(sk, set) match {
              case Some(xformed) =>
                realTimeValue.processReferenceEvent(xformed, sk)
                val SetReference(id, key, refType, value, versio) = xformed
                Some(RemoteReferenceSet(this.resourceId, sk, id, key, refType, value))
              case None =>
                None
            }
          case cleared: ClearReference =>
            realTimeValue.processReferenceEvent(cleared, sk)
            val ClearReference(id, key) = cleared
            Some(RemoteReferenceCleared(resourceId, sk, id, key))
        }
      case None =>
        // TODO we just drop the event because we don't have a RTV with this id.
        // later on I would like to keep some history to know if we ever had
        // an RTV with this id, else throw an error.
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

  def references(): Set[ReferenceState] = {
    this.references(this.data)
  }

  def references(value: RealTimeValue): Set[ReferenceState] = {
    value match {
      case v: RealTimeContainerValue =>
        val mine = v.references().map { x => toReferenceState(x) }
        val childrens = v.children.flatMap { child =>
          references(child)
        }.toSet
        mine ++ childrens
      case v: Any =>
        value.references().map { x => toReferenceState(x) }
    }
  }

  def toReferenceState(r: ModelReference[_]): ReferenceState = {
    val refType = r match {
      case ref: IndexReference => ReferenceType.Index
      case ref: RangeReference => ReferenceType.Range
      case _ => throw new IllegalArgumentException("Unexpected reference type")
    }

    ReferenceState(
      r.sessionId,
      r.modelValue.id,
      r.key,
      refType,
      r.get)
  }
}
