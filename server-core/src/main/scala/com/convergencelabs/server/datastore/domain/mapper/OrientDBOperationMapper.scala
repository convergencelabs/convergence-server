package com.convergencelabs.server.datastore.domain.mapper

import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import com.convergencelabs.server.domain.model.ot.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.Operation
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import ArrayInsertOperationMapper.ArrayInsertOperationToODocument
import ArrayInsertOperationMapper.{ DocumentClassName => ArrayInsertDocName }
import ArrayInsertOperationMapper.ODocumentToArrayInsertOperation
import ArrayMoveOperationMapper.ArrayMoveOperationToODocument
import ArrayMoveOperationMapper.{ DocumentClassName => ArrayMoveDocName }
import ArrayMoveOperationMapper.ODocumentToArrayMoveOperation
import ArrayRemoveOperationMapper.ArrayRemoveOperationToODocument
import ArrayRemoveOperationMapper.{ DocumentClassName => ArrayRemoveDocName }
import ArrayRemoveOperationMapper.ODocumentToArrayRemoveOperation
import ArrayReplaceOperationMapper.ArrayReplaceOperationToODocument
import ArrayReplaceOperationMapper.{ DocumentClassName => ArrayReplaceDocName }
import ArrayReplaceOperationMapper.ODocumentToArrayReplaceOperation
import ArraySetOperationMapper.ArraySetOperationToODocument
import ArraySetOperationMapper.{ DocumentClassName => ArraySetDocName }
import ArraySetOperationMapper.ODocumentToArraySetOperation
import CompoundOperationMapper.CompoundOperationToODocument
import CompoundOperationMapper.{ DocumentClassName => CompoundDocName }
import CompoundOperationMapper.ODocumentToCompoundOperation
import NumberAddOperationMapper.{ DocumentClassName => NumberAddDocName }
import NumberAddOperationMapper.NumberAddOperationToODocument
import NumberAddOperationMapper.ODocumentToNumberAddOperation
import NumberSetOperationMapper.{ DocumentClassName => NumberSetDocName }
import NumberSetOperationMapper.NumberSetOperationToODocument
import NumberSetOperationMapper.ODocumentToNumberSetOperation
import ObjectAddPropertyOperationMapper.{ DocumentClassName => ObjectAddPropertyDocName }
import ObjectAddPropertyOperationMapper.ODocumentToObjectAddPropertyOperation
import ObjectAddPropertyOperationMapper.ObjectAddPropertyOperationToODocument
import ObjectRemovePropertyOperationMapper.{ DocumentClassName => ObjectRemovePropertyDocName }
import ObjectRemovePropertyOperationMapper.ODocumentToObjectRemovePropertyOperation
import ObjectRemovePropertyOperationMapper.ObjectRemovePropertyOperationToODocument
import ObjectSetOperationMapper.{ DocumentClassName => ObjectSetDocName }
import ObjectSetOperationMapper.ODocumentToObjectSetOperation
import ObjectSetOperationMapper.ObjectSetOperationToODocument
import ObjectSetPropertyOperationMapper.{ DocumentClassName => ObjectSetPropertyDocName }
import ObjectSetPropertyOperationMapper.ODocumentToObjectSetPropertyOperation
import ObjectSetPropertyOperationMapper.ObjectSetPropertyOperationToODocument
import StringInsertOperationMapper.{ DocumentClassName => StringInsertDocName }
import StringInsertOperationMapper.ODocumentToStringInsertOperation
import StringInsertOperationMapper.StringInsertOperationToODocument
import StringRemoveOperationMapper.{ DocumentClassName => StringRemoveDocName }
import StringRemoveOperationMapper.ODocumentToStringRemoveOperation
import StringRemoveOperationMapper.StringRemoveOperationToODocument
import StringSetOperationMapper.{ DocumentClassName => StringSetDocName }
import StringSetOperationMapper.ODocumentToStringSetOperation
import StringSetOperationMapper.StringSetOperationToODocument

object OrientDBOperationMapper {

  def oDocumentToOperation(opAsDoc: ODocument): Operation = {
    opAsDoc.getClassName match {
      case CompoundDocName => opAsDoc.asCompoundOperation
      case _ => oDocumentToDiscreteOperation(opAsDoc)
    }
  }

  // scalastyle:off cyclomatic.complexity
  private[mapper] def oDocumentToDiscreteOperation(doc: ODocument): DiscreteOperation = {
    doc.getClassName match {
      case StringInsertDocName => doc.asStringInsertOperation
      case StringRemoveDocName => doc.asStringRemoveOperation
      case StringSetDocName => doc.asStringSetOperation

      case ArrayInsertDocName => doc.asArrayInsertOperation
      case ArrayRemoveDocName => doc.asArrayRemoveOperation
      case ArrayReplaceDocName => doc.asArrayReplaceOperation
      case ArrayMoveDocName => doc.asArrayMoveOperation
      case ArraySetDocName => doc.asArraySetOperation

      case ObjectAddPropertyDocName => doc.asObjectAddPropertyOperation
      case ObjectSetPropertyDocName => doc.asObjectSetPropertyOperation
      case ObjectRemovePropertyDocName => doc.asObjectRemovePropertyOperation
      case ObjectSetDocName => doc.asObjectSetOperation

      case NumberAddDocName => doc.asNumberAddOperation
      case NumberSetDocName => doc.asNumberSetOperation
    }
  }
  // scalastyle:on cyclomatic.complexity

  def operationToODocument(op: Operation): ODocument = {
    op match {
      case op: CompoundOperation => op.asODocument
      case op: DiscreteOperation => discreteOperationToODocument(op)
    }
  }

  // scalastyle:off cyclomatic.complexity
  private[this] def discreteOperationToODocument(op: DiscreteOperation): ODocument = {
    op match {
      // String Operations
      case op: StringInsertOperation => op.asODocument
      case op: StringRemoveOperation => op.asODocument
      case op: StringSetOperation => op.asODocument

      // Array Operations
      case op: ArrayInsertOperation => op.asODocument
      case op: ArrayRemoveOperation => op.asODocument
      case op: ArrayMoveOperation => op.asODocument
      case op: ArrayReplaceOperation => op.asODocument
      case op: ArraySetOperation => op.asODocument

      // Object Operations
      case op: ObjectSetPropertyOperation => op.asODocument
      case op: ObjectAddPropertyOperation => op.asODocument
      case op: ObjectRemovePropertyOperation => op.asODocument
      case op: ObjectSetOperation => op.asODocument

      // Number Operations
      case op: NumberAddOperation => op.asODocument
      case op: NumberSetOperation => op.asODocument
    }
  }
  // scalastyle:on cyclomatic.complexity
}
