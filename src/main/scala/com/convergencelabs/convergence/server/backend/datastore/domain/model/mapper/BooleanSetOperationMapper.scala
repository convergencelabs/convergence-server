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
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedBooleanSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.language.implicitConversions

object BooleanSetOperationMapper extends ODocumentMapper {

  private[domain] implicit class BooleanSetOperationToODocument(val s: AppliedBooleanSetOperation) extends AnyVal {
    def asODocument: ODocument = numberSetOperationToODocument(s)
  }

  private[domain] implicit def numberSetOperationToODocument(op: AppliedBooleanSetOperation): ODocument = {
    val AppliedBooleanSetOperation(id, noOp, value, oldValue) = op
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Val, value)
    doc.field(Fields.OldValue, oldValue.getOrElse(null))
    doc
  }

  private[domain] implicit class ODocumentToBooleanSetOperation(val d: ODocument) extends AnyVal {
    def asBooleanSetOperation: AppliedBooleanSetOperation = oDocumentToBooleanSetOperation(d)
  }

  private[domain] implicit def oDocumentToBooleanSetOperation(doc: ODocument): AppliedBooleanSetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[Boolean]
    val oldValue = doc.field(Fields.OldValue).asInstanceOf[Boolean]
    AppliedBooleanSetOperation(id, noOp, value, Option(oldValue))
  }

  private[domain] val DocumentClassName = "BooleanSetOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Val = "val"
    val OldValue = "oldVal"
  }
}
