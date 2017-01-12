package com.convergencelabs.server.frontend.realtime

import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JDouble
import com.convergencelabs.server.domain.model.ot.AppliedOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.server.domain.model.ot.AppliedDiscreteOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation

private[realtime] object ModelOperationMapper {

  def mapOutgoing(modelOp: ModelOperation): ModelOperationData = {
    val ModelOperation(ModelFqn(collectionId, modelId), version, timestamp, username, sid, op) = modelOp
    ModelOperationData(collectionId, modelId, version, timestamp.toEpochMilli, username, sid,
      op match {
        case operation: AppliedCompoundOperation => mapOutgoingCompound(operation)
        case operation: AppliedDiscreteOperation => mapOutgoingDiscrete(operation)
      })
  }

  def mapOutgoingCompound(op: AppliedCompoundOperation): AppliedCompoundOperationData = {
    AppliedCompoundOperationData(op.operations.map(op => mapOutgoingDiscrete(op).asInstanceOf[AppliedDiscreteOperationData]))
  }

  // scalastyle:off cyclomatic.complexity
  def mapOutgoingDiscrete(op: AppliedDiscreteOperation): AppliedDiscreteOperationData = {
    op match {
      case AppliedStringInsertOperation(id, noOp, index, value)                => AppliedStringInsertOperationData(id, noOp, index, value)
      case AppliedStringRemoveOperation(id, noOp, index, length, oldValue)     => AppliedStringRemoveOperationData(id, noOp, index, length, oldValue)
      case AppliedStringSetOperation(id, noOp, value, oldValue)                => AppliedStringSetOperationData(id, noOp, value, oldValue)

      case AppliedArrayInsertOperation(id, noOp, idx, newVal)                  => AppliedArrayInsertOperationData(id, noOp, idx, newVal)
      case AppliedArrayRemoveOperation(id, noOp, idx, oldValue)                => AppliedArrayRemoveOperationData(id, noOp, idx, oldValue)
      case AppliedArrayMoveOperation(id, noOp, fromIdx, toIdx)                 => AppliedArrayMoveOperationData(id, noOp, fromIdx, toIdx)
      case AppliedArrayReplaceOperation(id, noOp, idx, newVal, oldValue)       => AppliedArrayReplaceOperationData(id, noOp, idx, newVal, oldValue)
      case AppliedArraySetOperation(id, noOp, array, oldValue)                 => AppliedArraySetOperationData(id, noOp, array, oldValue)

      case AppliedObjectSetPropertyOperation(id, noOp, prop, newVal, oldValue) => AppliedObjectSetPropertyOperationData(id, noOp, prop, newVal, oldValue)
      case AppliedObjectAddPropertyOperation(id, noOp, prop, newVal)           => AppliedObjectAddPropertyOperationData(id, noOp, prop, newVal)
      case AppliedObjectRemovePropertyOperation(id, noOp, prop, oldValue)      => AppliedObjectRemovePropertyOperationData(id, noOp, prop, oldValue)
      case AppliedObjectSetOperation(id, noOp, objectData, oldValue)           => AppliedObjectSetOperationData(id, noOp, objectData, oldValue)

      case AppliedNumberAddOperation(id, noOp, delta)                          => AppliedNumberAddOperationData(id, noOp, delta)
      case AppliedNumberSetOperation(id, noOp, number, oldValue)               => AppliedNumberSetOperationData(id, noOp, number, oldValue)

      case AppliedBooleanSetOperation(id, noOp, value, oldValue)               => AppliedBooleanSetOperationData(id, noOp, value, oldValue)
      case AppliedDateSetOperation(id, noOp, value, oldValue)                  => AppliedDateSetOperationData(id, noOp, value, oldValue)
    }
  }
  // scalastyle:on cyclomatic.complexity
}
