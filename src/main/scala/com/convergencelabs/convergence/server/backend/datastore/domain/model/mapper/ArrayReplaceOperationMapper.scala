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
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedArrayReplaceOperation
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument

object ArrayReplaceOperationMapper extends ODocumentMapper {

  private[domain] def arrayReplaceOperationToODocument(obj: AppliedArrayReplaceOperation): ODocument = {
    val AppliedArrayReplaceOperation(id, noOp, index, value, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.Val, dataValueToODocument(value), OType.EMBEDDED)
    val oldValDoc = oldValue.map(dataValueToODocument)
    doc.field(Fields.OldValue, oldValDoc.orNull)
    doc
  }

  private[domain] def oDocumentToArrayReplaceOperation(doc: ODocument): AppliedArrayReplaceOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Fields.Idx).asInstanceOf[Int]
    val value = oDocumentToDataValue(doc.field(Fields.Val).asInstanceOf[ODocument])
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[ODocument]).map(oDocumentToDataValue)
    AppliedArrayReplaceOperation(id, noOp, idx, value, oldValue)
  }

  private[domain] val DocumentClassName = "ArrayReplaceOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
    val OldValue = "oldVal"
  }
}
