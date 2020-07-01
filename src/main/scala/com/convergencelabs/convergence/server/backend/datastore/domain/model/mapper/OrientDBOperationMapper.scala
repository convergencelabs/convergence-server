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

package com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper

import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArrayInsertOperationMapper.{ArrayInsertOperationToODocument, ODocumentToArrayInsertOperation, DocumentClassName => ArrayInsertDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArrayMoveOperationMapper.{ArrayMoveOperationToODocument, ODocumentToArrayMoveOperation, DocumentClassName => ArrayMoveDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArrayRemoveOperationMapper.{ArrayRemoveOperationToODocument, ODocumentToArrayRemoveOperation, DocumentClassName => ArrayRemoveDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArrayReplaceOperationMapper.{ArrayReplaceOperationToODocument, ODocumentToArrayReplaceOperation, DocumentClassName => ArrayReplaceDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArraySetOperationMapper.{ArraySetOperationToODocument, ODocumentToArraySetOperation, DocumentClassName => ArraySetDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.BooleanSetOperationMapper.{BooleanSetOperationToODocument, ODocumentToBooleanSetOperation, DocumentClassName => BooleanSetDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.CompoundOperationMapper.{CompoundOperationToODocument, ODocumentToCompoundOperation, DocumentClassName => CompoundDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DateSetOperationMapper.{DateSetOperationToODocument, ODocumentToDateSetOperation, DocumentClassName => DateSetDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.NumberAddOperationMapper.{NumberAddOperationToODocument, ODocumentToNumberAddOperation, DocumentClassName => NumberAddDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.NumberSetOperationMapper.{NumberSetOperationToODocument, ODocumentToNumberSetOperation, DocumentClassName => NumberSetDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectAddPropertyOperationMapper.{ODocumentToObjectAddPropertyOperation, ObjectAddPropertyOperationToODocument, DocumentClassName => ObjectAddPropertyDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectRemovePropertyOperationMapper.{ODocumentToObjectRemovePropertyOperation, ObjectRemovePropertyOperationToODocument, DocumentClassName => ObjectRemovePropertyDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectSetOperationMapper.{ODocumentToObjectSetOperation, ObjectSetOperationToODocument, DocumentClassName => ObjectSetDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectSetPropertyOperationMapper.{ODocumentToObjectSetPropertyOperation, ObjectSetPropertyOperationToODocument, DocumentClassName => ObjectSetPropertyDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.StringInsertOperationMapper.{ODocumentToStringInsertOperation, StringInsertOperationToODocument, DocumentClassName => StringInsertDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.StringRemoveOperationMapper.{ODocumentToStringRemoveOperation, StringRemoveOperationToODocument, DocumentClassName => StringRemoveDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.StringSetOperationMapper.{ODocumentToStringSetOperation, StringSetOperationToODocument, DocumentClassName => StringSetDocName}
import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.orientechnologies.orient.core.record.impl.ODocument

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

      // Boolean Operations
      case op: AppliedBooleanSetOperation => op.asODocument
      
      // Date Operations
      case op: AppliedDateSetOperation => op.asODocument
    }
  }
  // scalastyle:on cyclomatic.complexity
}
