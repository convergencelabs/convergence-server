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

import com.convergencelabs.convergence.server.backend.datastore.ODocumentMapper
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DataValueMapper._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedArrayInsertOperation
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument

object ArrayInsertOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArrayInsertOperationToODocument(val s: AppliedArrayInsertOperation) extends AnyVal {
    def asODocument: ODocument = arrayInsertOperationToODocument(s)
  }

  private[domain] def arrayInsertOperationToODocument(obj: AppliedArrayInsertOperation): ODocument = {
    val AppliedArrayInsertOperation(id, noOp, index, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.Val, dataValueToODocument(value), OType.EMBEDDED)
    doc
  }

  private[domain] def oDocumentToArrayInsertOperation(doc: ODocument): AppliedArrayInsertOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Fields.Idx).asInstanceOf[Int]
    val value = oDocumentToDataValue(doc.field(Fields.Val).asInstanceOf[ODocument])
    AppliedArrayInsertOperation(id, noOp, idx, value)
  }

  private[domain] val DocumentClassName = "ArrayInsertOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
  }
}
