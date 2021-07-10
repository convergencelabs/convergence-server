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

import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArrayInsertOperationMapper.{arrayInsertOperationToODocument, oDocumentToArrayInsertOperation, DocumentClassName => ArrayInsertDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArrayMoveOperationMapper.{arrayMoveOperationToODocument, oDocumentToArrayMoveOperation, DocumentClassName => ArrayMoveDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArrayRemoveOperationMapper.{arrayRemoveOperationToODocument, oDocumentToArrayRemoveOperation, DocumentClassName => ArrayRemoveDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArrayReplaceOperationMapper.{arrayReplaceOperationToODocument, oDocumentToArrayReplaceOperation, DocumentClassName => ArrayReplaceDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArraySetOperationMapper.{arraySetOperationToODocument, oDocumentToArraySetOperation, DocumentClassName => ArraySetDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.BooleanSetOperationMapper.{booleanSetOperationToODocument, oDocumentToBooleanSetOperation, DocumentClassName => BooleanSetDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.CompoundOperationMapper.{compoundOperationToODocument, oDocumentToCompoundOperation, DocumentClassName => CompoundDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DateSetOperationMapper.{dateSetOperationToODocument, oDocumentToDateSetOperation, DocumentClassName => DateSetDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.NumberAddOperationMapper.{numberAddOperationToODocument, oDocumentToNumberAddOperation, DocumentClassName => NumberAddDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.NumberSetOperationMapper.{numberSetOperationToODocument, oDocumentToNumberSetOperation, DocumentClassName => NumberSetDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectAddPropertyOperationMapper.{oDocumentToObjectAddPropertyOperation, objectAddPropertyOperationToODocument, DocumentClassName => ObjectAddPropertyDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectRemovePropertyOperationMapper.{oDocumentToObjectRemovePropertyOperation, objectRemovePropertyOperationToODocument, DocumentClassName => ObjectRemovePropertyDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectSetOperationMapper.{oDocumentToObjectSetOperation, objectSetOperationToODocument, DocumentClassName => ObjectSetDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectSetPropertyOperationMapper.{oDocumentToObjectSetPropertyOperation, objectSetPropertyOperationToODocument, DocumentClassName => ObjectSetPropertyDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.StringSetOperationMapper.{oDocumentToStringSetOperation, stringSetOperationToODocument, DocumentClassName => StringSetDocName}
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.StringSpliceOperationMapper.{oDocumentToStringSpliceOperation, stringSpliceOperationToODocument, DocumentClassName => StringSpliceDocName}
import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.orientechnologies.orient.core.record.impl.ODocument

object OrientDBOperationMapper {

  def oDocumentToOperation(opAsDoc: ODocument): AppliedOperation = {
    opAsDoc.getClassName match {
      case CompoundDocName => oDocumentToCompoundOperation(opAsDoc)
      case _ => oDocumentToDiscreteOperation(opAsDoc)
    }
  }

  // scalastyle:off cyclomatic.complexity
  private[mapper] def oDocumentToDiscreteOperation(doc: ODocument): AppliedDiscreteOperation = {
    doc.getClassName match {
      case StringSpliceDocName => oDocumentToStringSpliceOperation(doc)
      case StringSetDocName => oDocumentToStringSetOperation(doc)

      case ArrayInsertDocName => oDocumentToArrayInsertOperation(doc)
      case ArrayRemoveDocName => oDocumentToArrayRemoveOperation(doc)
      case ArrayReplaceDocName => oDocumentToArrayReplaceOperation(doc)
      case ArrayMoveDocName => oDocumentToArrayMoveOperation(doc)
      case ArraySetDocName => oDocumentToArraySetOperation(doc)

      case ObjectAddPropertyDocName => oDocumentToObjectAddPropertyOperation(doc)
      case ObjectSetPropertyDocName => oDocumentToObjectSetPropertyOperation(doc)
      case ObjectRemovePropertyDocName => oDocumentToObjectRemovePropertyOperation(doc)
      case ObjectSetDocName => oDocumentToObjectSetOperation(doc)

      case NumberAddDocName => oDocumentToNumberAddOperation(doc)
      case NumberSetDocName => oDocumentToNumberSetOperation(doc)

      case BooleanSetDocName => oDocumentToBooleanSetOperation(doc)

      case DateSetDocName => oDocumentToDateSetOperation(doc)
    }
  }
  // scalastyle:on cyclomatic.complexity

  def operationToODocument(op: AppliedOperation): ODocument = {
    op match {
      case op: AppliedCompoundOperation => compoundOperationToODocument(op)
      case op: AppliedDiscreteOperation => discreteOperationToODocument(op)
    }
  }

  // scalastyle:off cyclomatic.complexity
  private[this] def discreteOperationToODocument(op: AppliedDiscreteOperation): ODocument = {
    op match {
      // String Operations
      case op: AppliedStringSpliceOperation => stringSpliceOperationToODocument(op)
      case op: AppliedStringSetOperation => stringSetOperationToODocument(op)

      // Array Operations
      case op: AppliedArrayInsertOperation => arrayInsertOperationToODocument(op)
      case op: AppliedArrayRemoveOperation => arrayRemoveOperationToODocument(op)
      case op: AppliedArrayMoveOperation => arrayMoveOperationToODocument(op)
      case op: AppliedArrayReplaceOperation => arrayReplaceOperationToODocument(op)
      case op: AppliedArraySetOperation => arraySetOperationToODocument(op)

      // Object Operations
      case op: AppliedObjectSetPropertyOperation => objectSetPropertyOperationToODocument(op)
      case op: AppliedObjectAddPropertyOperation => objectAddPropertyOperationToODocument(op)
      case op: AppliedObjectRemovePropertyOperation => objectRemovePropertyOperationToODocument(op)
      case op: AppliedObjectSetOperation => objectSetOperationToODocument(op)

      // Number Operations
      case op: AppliedNumberAddOperation => numberAddOperationToODocument(op)
      case op: AppliedNumberSetOperation => numberSetOperationToODocument(op)

      // Boolean Operations
      case op: AppliedBooleanSetOperation => booleanSetOperationToODocument(op)

      // Date Operations
      case op: AppliedDateSetOperation => dateSetOperationToODocument(op)
    }
  }
  // scalastyle:on cyclomatic.complexity
}
