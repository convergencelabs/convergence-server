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

package com.convergencelabs.convergence.server.backend.services.domain.model

import com.convergencelabs.convergence.server.api.realtime.ModelClientActor._
import com.convergencelabs.convergence.server.backend.services.domain.model.RealtimeModelActor.{apply => _, _}
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.{Operation, _}
import com.convergencelabs.convergence.server.backend.services.domain.model.reference._
import com.convergencelabs.convergence.server.backend.services.domain.model.value.{RealtimeArray, RealtimeBoolean, RealtimeContainerValue, RealtimeDate, RealtimeDouble, RealtimeObject, RealtimeString, RealtimeValue, RealtimeValueFactory}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.model
import com.convergencelabs.convergence.server.model.domain.model._
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId

import java.time.Instant
import scala.util.{Failure, Success, Try}

/**
 * The [[RealtimeModel]] class represents an in memory version of a model.
 *
 * @param domainId The id of the domain this model belongs to.
 * @param modelId  The unique model id of this model.
 * @param cc       the server side concurrency control that will transform operations.
 * @param root     The root value of this model.
 */
private[model] final class RealtimeModel(val domainId: DomainId,
                                         val modelId: String,
                                         cc: ServerConcurrencyControl,
                                         root: ObjectValue) extends RealtimeValueFactory {

  val idToValue: collection.mutable.HashMap[String, RealtimeValue] = collection.mutable.HashMap[String, RealtimeValue]()
  private val elementReferenceManager = new ModelReferenceManager(source = this)

  val data: RealtimeObject = this.createValue(root, None, None).asInstanceOf[RealtimeObject]

  def contextVersion(): Long = {
    this.cc.contextVersion
  }

  def clientConnected(session: DomainSessionAndUserId, contextVersion: Long): Unit = {
    this.cc.trackClient(session.sessionId, contextVersion)
  }

  def clientDisconnected(session: DomainSessionAndUserId): Unit = {
    this.cc.untrackClient(session.sessionId)
    this.data.sessionDisconnected(session)
    this.elementReferenceManager.sessionDisconnected(session)
  }

  override def createValue(value: DataValue,
                           parent: Option[RealtimeContainerValue],
                           parentField: Option[Any]): RealtimeValue = {
    val result = super.createValue(value, parent, parentField)
    this.registerValue(result)
    result.addDetachListener(_ => this.unregisterValue(result))
    result
  }

  def processOperationEvent(unprocessed: UnprocessedOperationEvent): Try[(ProcessedOperationEvent, AppliedOperation)] = {
    for {
      preprocessed <- preprocessAndValidateOperationEvent(unprocessed)
      processed <- cc.processRemoteOperation(preprocessed)
      // FIXME this isn't quite right, if applying the operation fails, just rolling back
      //  the CC may not be enough, especially in the case of a compound operation,
      //  we may have partially mutated the model.
      result <- applyOperation(processed.operation) match {
        case Success(appliedOperation) =>
          cc.commit()
          Success(processed, appliedOperation)
        case Failure(f) =>
          cc.rollback()
          Failure(f)
      }
    } yield result
  }

  private[this] def preprocessAndValidateOperationEvent(unprocessed: UnprocessedOperationEvent): Try[UnprocessedOperationEvent] =
    preprocessAndValidateOperation(unprocessed.operation).map(op => unprocessed.copy(operation = op))

  private[this] def preprocessAndValidateOperation(op: Operation): Try[Operation] =
    op match {
      case c: CompoundOperation =>
        preprocessAndValidateCompoundOperation(c)
      case d: DiscreteOperation =>
        preprocessAndValidateDiscreteOperation(d)
    }

  private[this] def preprocessAndValidateCompoundOperation(op: CompoundOperation): Try[Operation] =
    op.operations
      .map(o => preprocessAndValidateDiscreteOperation(o))
      .partitionMap(_.toEither) match {
      case (Nil, ops) =>
        Success(op.copy(operations = ops))
      case (fail, _) =>
        Failure(fail.head)
    }

  private[this] def preprocessAndValidateDiscreteOperation(op: DiscreteOperation): Try[DiscreteOperation] =
    if (op.id.isEmpty) {
      Failure(new IllegalArgumentException("The value id of an operation can not be empty"))
    } else if (this.idToValue.contains(op.id)) {
      Success(op)
    } else {
      Success(op.clone(noOp = true))
    }

  private[this] def registerValue(realTimeValue: RealtimeValue): Unit =
    this.idToValue += (realTimeValue.id -> realTimeValue)

  private[this] def unregisterValue(realTimeValue: RealtimeValue): Unit =
    this.idToValue -= realTimeValue.id

  private[this] def applyOperation(op: Operation): Try[AppliedOperation] =
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

  def processReferenceEvent(event: ModelReferenceEvent): Try[Option[RemoteReferenceEvent]] =
    event.valueId match {
      case Some(valueId) =>
        handleNonElementReference(valueId, event)
      case None =>
        // This handles element references which have no id.
        handleElementReference(event)
    }

  private[this] def handleNonElementReference(valueId: String, event: ModelReferenceEvent): Try[Option[RemoteReferenceEvent]] = {
    val session = event.session
    idToValue.get(valueId) match {
      case Some(realTimeValue) =>
        event match {
          case share: ShareReference =>
            realTimeValue.processReferenceEvent(share, session)
            val ShareReference(domainFqn, _, _, id, key, values, contextVersion) = share

            val refVal: ReferenceValue[ModelReferenceValues] = ReferenceValue(values, contextVersion)
            this.cc.processRemoteReferenceSet(session.sessionId, valueId, refVal) match {
              case Some(xformed) =>
                val setRef: SetReference = SetReference(domainFqn, modelId, session, id, key, xformed.referenceValues, xformed.contextVersion.toInt)
                realTimeValue.processReferenceEvent(setRef, session).map { _ =>
                  Some(RemoteReferenceShared(modelId, session, setRef.valueId, setRef.key, setRef.values))
                }
              case None =>
                Success(None)
            }

          case unshare: UnShareReference =>
            realTimeValue.processReferenceEvent(unshare, session).map { _ =>
              val UnShareReference(_, modelId, _, id, key) = unshare
              Some(RemoteReferenceUnshared(modelId, session, id, key))

            }

          case set: SetReference =>
            val refVal: ReferenceValue[ModelReferenceValues] = ReferenceValue(set.values, set.contextVersion)
            this.cc.processRemoteReferenceSet(session.sessionId, valueId, refVal) match {
              case Some(xFormed) =>
                val setRef: SetReference = SetReference(domainId, modelId, session, set.valueId, set.key, xFormed.referenceValues, xFormed.contextVersion.toInt)
                realTimeValue.processReferenceEvent(setRef, session).map { _ =>
                  Some(RemoteReferenceSet(modelId, session, setRef.valueId, setRef.key, setRef.values))
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
        //  later on I would like to keep some history to know if we ever had
        //  an RTV with this id, else throw an error.
        Success(None)
    }
  }

  private[this] def handleElementReference(event: ModelReferenceEvent): Try[Option[RemoteReferenceEvent]] = {
    val session = event.session
    event match {
      case share: ShareReference =>
        elementReferenceManager.handleReferenceEvent(share)
        val ShareReference(_, _, _, id, key, values, contextVersion) = share
        val xFormedValue = values.asInstanceOf[ElementReferenceValues].values filter {
          idToValue.contains
        }
        val elementValues = ElementReferenceValues(xFormedValue)
        val xFormedShare = SetReference(domainId, modelId, session, id, key, elementValues, contextVersion)
        elementReferenceManager.handleReferenceEvent(xFormedShare).map { _ =>
          Some(RemoteReferenceShared(modelId, session, id, key, elementValues))
        }

      case unshare: UnShareReference =>
        elementReferenceManager.handleReferenceEvent(unshare).map { _ =>
          val UnShareReference(_, _, _, id, key) = unshare
          Some(RemoteReferenceUnshared(modelId, session, id, key))
        }

      case set: SetReference =>
        val SetReference(d, m, s, id, key, values, version) = set
        val xFormedValue = values.asInstanceOf[ElementReferenceValues].values filter {
          idToValue.contains
        }
        val elementValues = ElementReferenceValues(xFormedValue)
        val xFormedSet = SetReference(d, m, s, id, key, elementValues, version)
        elementReferenceManager.handleReferenceEvent(xFormedSet).map { _ =>
          Some(RemoteReferenceSet(modelId, session, id, key, elementValues))
        }

      case cleared: ClearReference =>
        elementReferenceManager.handleReferenceEvent(cleared).map { _ =>
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
      val modelValue = this.idToValue.get(op.id)

      Success(op match {
        // FIXME we have an issue here where we don't know what to put for the old value
        //  if the element is not longer in the model. The database requires an
        //  old value. So we need to jam something in for now.
        //  https://github.com/convergencelabs/convergence-project/issues/266
        case StringSpliceOperation(id, noOp, index, deleteCount, insertValue) =>
          AppliedStringSpliceOperation(id, noOp, index, deleteCount, None, insertValue)

        case StringSetOperation(id, noOp, value) =>
          val oldValue = modelValue
            .map(v => v.asInstanceOf[RealtimeString].data()).orElse(Some(""))
          AppliedStringSetOperation(id, noOp, value, oldValue)

        case ObjectSetPropertyOperation(id, noOp, property, value) =>
          val oldValue = modelValue
            .map(v => v.asInstanceOf[RealtimeObject].valueAt(List(property)).get.dataValue())
            .orElse(Some(NullValue("unknown old value")))
          AppliedObjectSetPropertyOperation(id, noOp, property, value, oldValue)

        case ObjectAddPropertyOperation(id, noOp, property, value) =>
          AppliedObjectAddPropertyOperation(id, noOp, property, value)

        case ObjectRemovePropertyOperation(id, noOp, property) =>
          val oldValue = modelValue
            .map(v => v.asInstanceOf[RealtimeObject].valueAt(List(property)).get.dataValue())
            .orElse(Some(NullValue("unknown old value")))
          AppliedObjectRemovePropertyOperation(id, noOp, property, oldValue)

        case ObjectSetOperation(id, noOp, value) =>
          val oldValue = modelValue
            .map(v => v.asInstanceOf[RealtimeObject].dataValue().children)
            .orElse(Some(Map[String, DataValue]()))
          AppliedObjectSetOperation(id, noOp, value, oldValue)

        case NumberAddOperation(id, noOp, value) =>
          AppliedNumberAddOperation(id, noOp, value)

        case NumberSetOperation(id, noOp, value) =>
          val oldValue = modelValue
            .map(v => v.asInstanceOf[RealtimeDouble].dataValue().value)
            .orElse(Some(0d))
          AppliedNumberSetOperation(id, noOp, value, oldValue)

        case BooleanSetOperation(id, noOp, value) =>
          val oldValue = modelValue
            .map(v => v.asInstanceOf[RealtimeBoolean].dataValue().value)
            .orElse(Some(false))
          AppliedBooleanSetOperation(id, noOp, value, oldValue)

        case ArrayInsertOperation(id, noOp, index, value) =>
          AppliedArrayInsertOperation(id, noOp, index, value)

        case ArrayRemoveOperation(id, noOp, index) =>
          val oldValue = modelValue
            .map(v => v.asInstanceOf[RealtimeArray].valueAt(List(index)).get.dataValue())
            .orElse(Some(NullValue("unknown old value")))
          AppliedArrayRemoveOperation(id, noOp, index, oldValue)

        case ArrayReplaceOperation(id, noOp, index, value) =>
          val oldValue = modelValue
            .map(v => v.asInstanceOf[RealtimeArray].valueAt(List(index)).get.dataValue())
            .orElse(Some(NullValue("unknown old value")))
          AppliedArrayRemoveOperation(id, noOp, index, oldValue)
          AppliedArrayReplaceOperation(id, noOp, index, value, None)

        case ArrayMoveOperation(id, noOp, fromIndex, toIndex) =>
          AppliedArrayMoveOperation(id, noOp, fromIndex, toIndex)

        case ArraySetOperation(id, noOp, value) =>
          val oldValue = modelValue
            .map(v => v.asInstanceOf[RealtimeArray].dataValue().children)
            .orElse(Some(List[DataValue]()))
          AppliedArraySetOperation(id, noOp, value, oldValue)

        case DateSetOperation(id, noOp, value) =>
          val oldValue = modelValue
            .map(v => v.asInstanceOf[RealtimeDate].dataValue().value)
            .orElse(Some(Instant.now()))
          AppliedDateSetOperation(id, noOp, value, oldValue)
      })
    }
  }

  def references(): Set[ReferenceState] = {
    val mine = elementReferenceManager.referenceMap().getAll.map { x => toReferenceState(x) }
    this.references(this.data) ++ mine
  }

  def references(value: RealtimeValue): Set[ReferenceState] = {
    value match {
      case v: RealtimeContainerValue =>
        val mine = v.references().map { x => toReferenceState(x) }
        val mappedChildren = v.children.flatMap { child =>
          references(child)
        }.toSet
        mine ++ mappedChildren
      case _: Any =>
        value.references().map { x => toReferenceState(x) }
    }
  }

  private[this] def toReferenceState(r: ModelReference[_, _]): ReferenceState = {
    model.ReferenceState(
      r.session,
      r.target match {
        case value: RealtimeValue =>
          Some(value.id)
        case _ =>
          None
      },
      r.key,
      r.referenceValues)
  }
}
