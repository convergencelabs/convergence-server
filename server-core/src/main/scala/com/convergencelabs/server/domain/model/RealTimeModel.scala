package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DateValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedDiscreteOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation
import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import com.convergencelabs.server.domain.model.ot.DateSetOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.Operation
import com.convergencelabs.server.domain.model.ot.ProcessedOperationEvent
import com.convergencelabs.server.domain.model.ot.ServerConcurrencyControl
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.convergencelabs.server.domain.model.ot.UnprocessedOperationEvent
import com.convergencelabs.server.domain.model.reference.ElementReference
import com.convergencelabs.server.domain.model.reference.ElementReferenceManager
import com.convergencelabs.server.domain.model.reference.IndexReference
import com.convergencelabs.server.domain.model.reference.ModelReference
import com.convergencelabs.server.domain.model.reference.RangeReference

class RealTimeModel(
  private[this] val domainFqn: DomainFqn,
  private[this] val modelId: String,
  private[this] val cc: ServerConcurrencyControl,
  private val obj: ObjectValue) extends RealTimeValueFactory {

  val idToValue = collection.mutable.HashMap[String, RealTimeValue]()
  val elementReferenceManager = new ElementReferenceManager(this, List(ReferenceType.Element))

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
    this.elementReferenceManager.sessionDisconnected(sk)
  }

  override def createValue(
    value: DataValue,
    parent: Option[RealTimeContainerValue],
    parentField: Option[Any]): RealTimeValue = {
    val result = super.createValue(value, parent, parentField)
    this.registerValue(result)
    result.addDetachListener(_ => this.unregisterValue(result))
    result
  }

  def processOperationEvent(unprocessed: UnprocessedOperationEvent): Try[(ProcessedOperationEvent, AppliedOperation)] = {
    // FIXME  We need to validate the operation (id != null for example)
    val preprocessed = unprocessed.copy(operation = noOpObsoleteOperations(unprocessed.operation))
    val processed = cc.processRemoteOperation(preprocessed)
    // FIXME this isn't quite right, if applying the operation fails, just rolling back
    // the CC may not be enough, especially in the case of a compound operation,
    // we may have partially mutated the model.
    applyOpperation(processed.operation) match {
      case Success(appliedOperation) =>
        cc.commit()
        Success(processed, appliedOperation)
      case Failure(f) =>
        cc.rollback()
        Failure(f)
    }
  }

  private[this] def registerValue(realTimeValue: RealTimeValue): Unit = {
    this.idToValue += (realTimeValue.id -> realTimeValue)
  }

  private[this] def unregisterValue(realTimeValue: RealTimeValue): Unit = {
    this.idToValue -= realTimeValue.id
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

  private[this] def applyOpperation(op: Operation): Try[AppliedOperation] = {
    op match {
      case c: CompoundOperation =>
        Try {
          val appliedOperations = c.operations map { o =>
            applyDiscreteOperation(o) match {
              case Failure(f) => throw f
              case Success(appliedOp: AppliedOperation) => appliedOp
            }
          }
          AppliedCompoundOperation(appliedOperations)
        }
      case d: DiscreteOperation =>
        applyDiscreteOperation(d)
    }
  }

  // fixme: refactor this
  def processReferenceEvent(event: ModelReferenceEvent, sk: SessionKey): Try[Option[RemoteReferenceEvent]] = Try {
    event.id match {
      case Some(id) =>
        idToValue.get(id) match {
          case Some(realTimeValue) =>
            event match {
              case publish: PublishReference =>
                realTimeValue.processReferenceEvent(publish, sk)
                val PublishReference(domainFqn, _, id, key, refType, values, contextVersion) = publish

                (values, contextVersion) match {
                  case (Some(values), Some(contextVersion)) =>
                    val refVal: ReferenceValue = ReferenceValue(id, key, refType, values, contextVersion)
                    this.cc.processRemoteReferenceSet(sk.toString(), refVal) match {
                      case Some(xformed) =>
                        val setRef: SetReference = SetReference(domainFqn, this.modelId, xformed.id, xformed.key, xformed.referenceType, xformed.values, xformed.contextVersion)
                        realTimeValue.processReferenceEvent(setRef, sk)
                        Some(RemoteReferencePublished(this.modelId, sk, setRef.id, setRef.key, setRef.referenceType, Some(setRef.values)))
                      case None =>
                        None
                    }
                  case _ =>
                    (Some(RemoteReferencePublished(modelId, sk, id, key, refType, None)))
                }

              case unpublish: UnpublishReference =>
                realTimeValue.processReferenceEvent(unpublish, sk)
                val UnpublishReference(domain, modelId, id, key) = unpublish
                Some(RemoteReferenceUnpublished(modelId, sk, id, key))
              case set: SetReference =>
                //TODO: Added ReferenceValue to move ot packages into separate project and need to evaluate usage here
                val refVal: ReferenceValue = ReferenceValue(set.id, set.key, set.referenceType, set.values, set.contextVersion)
                this.cc.processRemoteReferenceSet(sk.toString(), refVal) match {
                  case Some(xformed) =>
                    val setRef: SetReference = SetReference(domainFqn, modelId, xformed.id, xformed.key, xformed.referenceType, xformed.values, xformed.contextVersion)
                    realTimeValue.processReferenceEvent(setRef, sk)
                    Some(RemoteReferenceSet(this.modelId, sk, setRef.id, setRef.key, setRef.referenceType, setRef.values))
                  case None =>
                    None
                }
              case cleared: ClearReference =>
                realTimeValue.processReferenceEvent(cleared, sk)
                val ClearReference(_, _, id, key) = cleared
                Some(RemoteReferenceCleared(this.modelId, sk, id, key))
            }
          case None =>
            // TODO we just drop the event because we don't have a RTV with this id.
            // later on I would like to keep some history to know if we ever had
            // an RTV with this id, else throw an error.
            None
        }
      case None =>
        // This handles element references which have no id.
        event match {
          case publish: PublishReference =>
            elementReferenceManager.handleReferenceEvent(publish, sk.toString())
            val PublishReference(_, _, id, key, refType, values, contextVersion) = publish
            (values, contextVersion) match {
              case (Some(values), Some(contextVersion)) =>
                val xformedValue = values.asInstanceOf[List[String]] filter { idToValue.contains(_) }
                val xformedSet = SetReference(domainFqn, modelId, id, key, refType, xformedValue, contextVersion)
                elementReferenceManager.handleReferenceEvent(xformedSet, sk.toString())
                Some(RemoteReferencePublished(modelId, sk, id, key, refType, Some(xformedValue)))
              case _ =>
                Some(RemoteReferencePublished(modelId, sk, id, key, refType, None))
            }

          case unpublish: UnpublishReference =>
            elementReferenceManager.handleReferenceEvent(unpublish, sk.toString())
            val UnpublishReference(_, _, id, key) = unpublish
            Some(RemoteReferenceUnpublished(modelId, sk, id, key))
          case set: SetReference =>
            val SetReference(d, m, id, key, refType, values, version) = set
            val xformedValue = values.asInstanceOf[List[String]] filter { idToValue.contains(_) }
            val xformedSet = SetReference(d, m, id, key, refType, xformedValue, version)
            elementReferenceManager.handleReferenceEvent(xformedSet, sk.toString())
            Some(RemoteReferenceSet(modelId, sk, id, key, refType, xformedValue))
          case cleared: ClearReference =>
            elementReferenceManager.handleReferenceEvent(cleared, sk.toString())
            val ClearReference(_, _, id, key) = cleared
            Some(RemoteReferenceCleared(modelId, sk, id, key))
        }
    }
  }

  def applyDiscreteOperation(op: DiscreteOperation): Try[AppliedDiscreteOperation] = {
    if (!op.noOp) {
      val value = this.idToValue(op.id)
      value.processOperation(op)
    } else {
      Success(op match {
        case StringRemoveOperation(id, noOp, index, value) => AppliedStringRemoveOperation(id, noOp, index, value.length(), None)
        case StringInsertOperation(id, noOp, index, value) => AppliedStringInsertOperation(id, noOp, index, value)
        case StringSetOperation(id, noOp, value) => AppliedStringSetOperation(id, noOp, value, None)
        case ObjectSetPropertyOperation(id, noOp, property, value) => AppliedObjectSetPropertyOperation(id, noOp, property, value, None)
        case ObjectAddPropertyOperation(id, noOp, property, value) => AppliedObjectAddPropertyOperation(id, noOp, property, value)
        case ObjectRemovePropertyOperation(id, noOp, property) => AppliedObjectRemovePropertyOperation(id, noOp, property, None)
        case ObjectSetOperation(id, noOp, value) => AppliedObjectSetOperation(id, noOp, value, None)
        case NumberAddOperation(id, noOp, value) => AppliedNumberAddOperation(id, noOp, value)
        case NumberSetOperation(id, noOp, value) => AppliedNumberSetOperation(id, noOp, value, None)
        case BooleanSetOperation(id, noOp, value) => AppliedBooleanSetOperation(id, noOp, value, None)
        case ArrayInsertOperation(id, noOp, index, value) => AppliedArrayInsertOperation(id, noOp, index, value)
        case ArrayRemoveOperation(id, noOp, index) => AppliedArrayRemoveOperation(id, noOp, index, None)
        case ArrayReplaceOperation(id, noOp, index, value) => AppliedArrayReplaceOperation(id, noOp, index, value, None)
        case ArrayMoveOperation(id, noOp, fromIndex, toIndex) => AppliedArrayMoveOperation(id, noOp, fromIndex, toIndex)
        case ArraySetOperation(id, noOp, value) => AppliedArraySetOperation(id, noOp, value, None)
        case DateSetOperation(id, noOp, value) => AppliedDateSetOperation(id, noOp, value, None)
      })
    }
  }

  def references(): Set[ReferenceState] = {
    val mine = elementReferenceManager.referenceMap().getAll().map { x => toReferenceState(x) }
    this.references(this.data) ++ mine
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
      case ref: ElementReference => ReferenceType.Element
      case _ => throw new IllegalArgumentException("Unexpected reference type")
    }

    ReferenceState(
      r.sessionId,
      r.modelValue match {
        case value: RealTimeValue =>
          Some(value.id)
        case _ =>
          None
      },
      r.key,
      refType,
      r.get)
  }
}
