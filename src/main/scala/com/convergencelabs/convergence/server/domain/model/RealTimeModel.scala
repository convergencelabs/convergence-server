/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.model

import com.convergencelabs.convergence.server.api.realtime.ModelClientActor._
import com.convergencelabs.convergence.server.domain.model.RealtimeModelActor.{apply => _, _}
import com.convergencelabs.convergence.server.domain.model.data.{DataValue, ObjectValue}
import com.convergencelabs.convergence.server.domain.model.ot._
import com.convergencelabs.convergence.server.domain.model.reference._
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserSessionId}

import scala.util.{Failure, Success, Try}

/**
 * The [[RealTimeModel]] class represents an in memory version of a model.
 *
 * @param domainId The id of the domain this model belongs to.
 * @param modelId  The unique model id of this model.
 * @param cc       the server side concurrency control that will transform operations.
 * @param root     The root value of this model.
 */
private[model] class RealTimeModel(domainId: DomainId,
                    modelId: String,
                    cc: ServerConcurrencyControl,
                    root: ObjectValue) extends RealTimeValueFactory {

  val idToValue: collection.mutable.HashMap[String, RealTimeValue] = collection.mutable.HashMap[String, RealTimeValue]()
  private val elementReferenceManager = new ElementReferenceManager(this, List(ReferenceType.Element))

  val data: RealTimeObject = this.createValue(root, None, None).asInstanceOf[RealTimeObject]

  def contextVersion(): Long = {
    this.cc.contextVersion
  }

  def clientConnected(session: DomainUserSessionId, contextVersion: Long): Unit = {
    this.cc.trackClient(session.sessionId, contextVersion)
  }

  def clientDisconnected(session: DomainUserSessionId): Unit = {
    this.cc.untrackClient(session.sessionId)
    this.data.sessionDisconnected(session)
    this.elementReferenceManager.sessionDisconnected(session)
  }

  override def createValue(value: DataValue,
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
    //  the CC may not be enough, especially in the case of a compound operation,
    //  we may have partially mutated the model.
    applyOperation(processed.operation) match {
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

  private[this] def applyOperation(op: Operation): Try[AppliedOperation] = {
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

  def processReferenceEvent(event: ModelReferenceEvent): Try[Option[RemoteReferenceEvent]] = {
    val session = event.session
    event.valueId match {
      case Some(valueId) =>
        handleNonElementReference(valueId, event)
      case None =>
        // This handles element references which have no id.
        handleElementReference(event)
    }
  }

  private[this] def handleNonElementReference(valueId: String, event: ModelReferenceEvent): Try[Option[RemoteReferenceEvent]] = {
    val session = event.session
    idToValue.get(valueId) match {
      case Some(realTimeValue) =>
        event match {
          case share: ShareReference =>
            realTimeValue.processReferenceEvent(share, session)
            val ShareReference(domainFqn, _, _, id, key, refType, values, contextVersion) = share

            val refVal: ReferenceValue = ReferenceValue(id, key, refType, values, contextVersion)
            this.cc.processRemoteReferenceSet(session.sessionId, refVal) match {
              case Some(xformed) =>
                val setRef: SetReference = SetReference(domainFqn, modelId, session, xformed.id, xformed.key, xformed.referenceType, xformed.values, xformed.contextVersion.toInt)
                realTimeValue.processReferenceEvent(setRef, session).map { _ =>
                  Some(RemoteReferenceShared(modelId, session, setRef.valueId, setRef.key, setRef.referenceType, setRef.values))
                }
              case None =>
                Success(None)
            }

          case unshare: UnshareReference =>
            realTimeValue.processReferenceEvent(unshare, session).map { _ =>
              val UnshareReference(_, modelId, _, id, key) = unshare
              Some(RemoteReferenceUnshared(modelId, session, id, key))

            }

          case set: SetReference =>
            val refVal: ReferenceValue = ReferenceValue(set.valueId, set.key, set.referenceType, set.values, set.contextVersion)
            this.cc.processRemoteReferenceSet(session.sessionId, refVal) match {
              case Some(xformed) =>
                val setRef: SetReference = SetReference(domainId, modelId, session, xformed.id, xformed.key, xformed.referenceType, xformed.values, xformed.contextVersion.toInt)
                realTimeValue.processReferenceEvent(setRef, session).map { _ =>
                  Some(RemoteReferenceSet(modelId, session, setRef.valueId, setRef.key, setRef.referenceType, setRef.values))
                }
              case None =>
                Success(None)
            }

          case cleared: ClearReference =>
            realTimeValue.processReferenceEvent(cleared, session).map { _ =>
              val ClearReference(_, _, _, id, key) = cleared
              Some(RemoteReferenceCleared(modelId, session, id, key))
            }
        }
      case None =>
        // TODO we just drop the event because we don't have a RTV with this id.
        // later on I would like to keep some history to know if we ever had
        // an RTV with this id, else throw an error.
        Success(None)
    }
  }

  private[this] def handleElementReference(event: ModelReferenceEvent): Try[Option[RemoteReferenceEvent]] = {
    val session = event.session
    event match {
      case share: ShareReference =>
        elementReferenceManager.handleReferenceEvent(share, session)
        val ShareReference(_, _, _, id, key, refType, values, contextVersion) = share
        val xformedValue = values.asInstanceOf[List[String]] filter {
          idToValue.contains
        }
        val xformedSet = SetReference(domainId, modelId, session, id, key, refType, xformedValue, contextVersion)
        elementReferenceManager.handleReferenceEvent(xformedSet, session).map { _ =>
          Some(RemoteReferenceShared(modelId, session, id, key, refType, xformedValue))
        }

      case unshare: UnshareReference =>
        elementReferenceManager.handleReferenceEvent(unshare, session).map { _ =>
          val UnshareReference(_, _, _, id, key) = unshare
          Some(RemoteReferenceUnshared(modelId, session, id, key))
        }

      case set: SetReference =>
        val SetReference(d, m, s, id, key, refType, values, version) = set
        val xformedValue = values.asInstanceOf[List[String]] filter {
          idToValue.contains
        }
        val xformedSet = SetReference(d, m, s, id, key, refType, xformedValue, version)
        elementReferenceManager.handleReferenceEvent(xformedSet, session).map { _ =>
          Some(RemoteReferenceSet(modelId, session, id, key, refType, xformedValue))
        }

      case cleared: ClearReference =>
        elementReferenceManager.handleReferenceEvent(cleared, session).map { _ =>
          val ClearReference(_, _, _, id, key) = cleared
          Some(RemoteReferenceCleared(modelId, session, id, key))
        }
    }
  }

  private[this] def applyDiscreteOperation(op: DiscreteOperation): Try[AppliedDiscreteOperation] = {
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
        val mappedChildren = v.children.flatMap { child =>
          references(child)
        }.toSet
        mine ++ mappedChildren
      case _: Any =>
        value.references().map { x => toReferenceState(x) }
    }
  }

  private[this] def toReferenceState(r: ModelReference[_]): ReferenceState = {
    val refType = r match {
      case _: IndexReference => ReferenceType.Index
      case _: RangeReference => ReferenceType.Range
      case _: ElementReference => ReferenceType.Element
      case _: Any => throw new IllegalArgumentException(s"Unexpected reference type: ${r.getClass.getSimpleName}")
    }

    ReferenceState(
      r.session,
      r.modelValue match {
        case value: RealTimeValue =>
          Some(value.id)
        case _ =>
          None
      },
      r.key,
      refType,
      r.get())
  }
}
