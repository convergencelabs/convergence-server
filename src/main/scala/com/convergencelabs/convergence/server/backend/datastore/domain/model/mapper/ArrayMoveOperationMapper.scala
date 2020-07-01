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
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedArrayMoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.language.implicitConversions

object ArrayMoveOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArrayMoveOperationToODocument(val s: AppliedArrayMoveOperation) extends AnyVal {
    def asODocument: ODocument = arrayMoveOperationToODocument(s)
  }

  private[domain] implicit def arrayMoveOperationToODocument(obj: AppliedArrayMoveOperation): ODocument = {
    val AppliedArrayMoveOperation(id, noOp, from, to) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.From, from)
    doc.field(Fields.To, to)
    doc
  }

  private[domain] implicit class ODocumentToArrayMoveOperation(val d: ODocument) extends AnyVal {
    def asArrayMoveOperation: AppliedArrayMoveOperation = oDocumentToArrayMoveOperation(d)
  }

  private[domain] implicit def oDocumentToArrayMoveOperation(doc: ODocument): AppliedArrayMoveOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val from = doc.field(Fields.From).asInstanceOf[Int]
    val to = doc.field(Fields.To).asInstanceOf[Int]
    AppliedArrayMoveOperation(id, noOp, from, to)
  }

  private[domain] val DocumentClassName = "ArrayMoveOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val From = "fromIdx"
    val To = "toIdx"
  }
}
