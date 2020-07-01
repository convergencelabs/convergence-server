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

import com.convergencelabs.convergence.server.backend.datastore.mapper.ODocumentMapper
import com.convergencelabs.convergence.server.domain.model.ot.AppliedStringRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.language.implicitConversions

object StringRemoveOperationMapper extends ODocumentMapper {

  private[domain] implicit class StringRemoveOperationToODocument(val s: AppliedStringRemoveOperation) extends AnyVal {
    def asODocument: ODocument = stringRemoveOperationToODocument(s)
  }

  private[domain] implicit def stringRemoveOperationToODocument(obj: AppliedStringRemoveOperation): ODocument = {
    val AppliedStringRemoveOperation(id, noOp, index, length, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.Length, length)
    doc.field(Fields.OldValue, oldValue.getOrElse(null))   
    doc
  }

  private[domain] implicit class ODocumentToStringRemoveOperation(val d: ODocument) extends AnyVal {
    def asStringRemoveOperation: AppliedStringRemoveOperation = oDocumentToStringRemoveOperation(d)
  }

  private[domain] implicit def oDocumentToStringRemoveOperation(doc: ODocument): AppliedStringRemoveOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val index = doc.field(Fields.Idx).asInstanceOf[Int]
    val length = doc.field(Fields.Length).asInstanceOf[Int]
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[String])
    AppliedStringRemoveOperation(id, noOp, index, length, oldValue)
  }

  private[domain] val DocumentClassName = "StringRemoveOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Length = "length"
    val Idx = "idx"
    val OldValue = "oldVal"
  }
}
