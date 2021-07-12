/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper

import com.convergencelabs.convergence.server.backend.datastore.ODocumentMapper
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedStringSpliceOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object StringSpliceOperationMapper extends ODocumentMapper {

  private[domain] def stringSpliceOperationToODocument(obj: AppliedStringSpliceOperation): ODocument = {
    val AppliedStringSpliceOperation(id, noOp, index, deleteCount, deletedValue, insertValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.DeleteCount, deleteCount)
    doc.field(Fields.DeletedVal, deletedValue.orNull)
    doc.field(Fields.InsertedVal, insertValue)
    doc
  }

  private[domain] def oDocumentToStringSpliceOperation(doc: ODocument): AppliedStringSpliceOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val index = doc.field(Fields.Idx).asInstanceOf[Int]
    val deleteCount = doc.field(Fields.DeleteCount).asInstanceOf[Int]
    val deletedValue = Option(doc.field(Fields.DeletedVal).asInstanceOf[String])
    val insertedVal = doc.field(Fields.InsertedVal).asInstanceOf[String]
    AppliedStringSpliceOperation(id, noOp, index, deleteCount, deletedValue, insertedVal)
  }

  private[domain] val DocumentClassName = "StringSpliceOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Idx = "idx"
    val DeleteCount = "deleteCount"
    val DeletedVal = "deletedVal"
    val InsertedVal = "insertedVal"
  }
}
