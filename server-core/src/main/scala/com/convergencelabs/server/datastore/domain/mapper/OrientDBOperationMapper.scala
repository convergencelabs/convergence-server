package com.convergencelabs.server.datastore.domain.mapper

import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
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
import BooleanSetOperationMapper.BooleanSetOperationToODocument
import BooleanSetOperationMapper.{ DocumentClassName => BooleanSetDocName }
import BooleanSetOperationMapper.ODocumentToBooleanSetOperation
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
import DateSetOperationMapper.{ DocumentClassName => DateSetDocName }
import DateSetOperationMapper.ODocumentToDateSetOperation
import DateSetOperationMapper.DateSetOperationToODocument
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation

object OrientDBOperationMapper {

  def oDocumentToOperation(opAsDoc: ODocument): AppliedOperation = {
    opAsDoc.getClassName match {
      case CompoundDocName => opAsDoc.asCompoundOperation
      case _ => oDocumentToDiscreteOperation(opAsDoc)
    }
  }

  // scalastyle:off cyclomatic.complexity
  private[mapper] def oDocumentToDiscreteOperation(doc: ODocument): AppliedDiscreteOperation = {
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

      case BooleanSetDocName => doc.asBooleanSetOperation
      
      case DateSetDocName => doc.asDateSetOperation
    }
  }
  // scalastyle:on cyclomatic.complexity

  def operationToODocument(op: AppliedOperation): ODocument = {
    op match {
      case op: AppliedCompoundOperation => op.asODocument
      case op: AppliedDiscreteOperation => discreteOperationToODocument(op)
    }
  }

  // scalastyle:off cyclomatic.complexity
  private[this] def discreteOperationToODocument(op: AppliedDiscreteOperation): ODocument = {
    op match {
      // String Operations
      case op: AppliedStringInsertOperation => op.asODocument
      case op: AppliedStringRemoveOperation => op.asODocument
      case op: AppliedStringSetOperation => op.asODocument

      // Array Operations
      case op: AppliedArrayInsertOperation => op.asODocument
      case op: AppliedArrayRemoveOperation => op.asODocument
      case op: AppliedArrayMoveOperation => op.asODocument
      case op: AppliedArrayReplaceOperation => op.asODocument
      case op: AppliedArraySetOperation => op.asODocument

      // Object Operations
      case op: AppliedObjectSetPropertyOperation => op.asODocument
      case op: AppliedObjectAddPropertyOperation => op.asODocument
      case op: AppliedObjectRemovePropertyOperation => op.asODocument
      case op: AppliedObjectSetOperation => op.asODocument

      // Number Operations
      case op: AppliedNumberAddOperation => op.asODocument
      case op: AppliedNumberSetOperation => op.asODocument

      case op: AppliedBooleanSetOperation => op.asODocument
      
      case op: AppliedDateSetOperation => op.asODocument
    }
  }
  // scalastyle:on cyclomatic.complexity
}
