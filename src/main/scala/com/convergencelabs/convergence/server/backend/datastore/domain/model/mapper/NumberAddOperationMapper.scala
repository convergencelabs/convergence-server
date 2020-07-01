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
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedNumberAddOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.language.implicitConversions

object NumberAddOperationMapper extends ODocumentMapper {

  private[domain] implicit class NumberAddOperationToODocument(val s: AppliedNumberAddOperation) extends AnyVal {
    def asODocument: ODocument = numberAddOperationToODocument(s)
  }

  private[domain] implicit def numberAddOperationToODocument(obj: AppliedNumberAddOperation): ODocument = {
    val AppliedNumberAddOperation(id, noOp, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Val, value)
    doc
  }

  private[domain] implicit class ODocumentToNumberAddOperation(val d: ODocument) extends AnyVal {
    def asNumberAddOperation: AppliedNumberAddOperation = oDocumentToNumberAddOperation(d)
  }

  private[domain] implicit def oDocumentToNumberAddOperation(doc: ODocument): AppliedNumberAddOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[Double]
    AppliedNumberAddOperation(id, noOp, value)
  }

  private[domain] val DocumentClassName = "NumberAddOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Val = "val"
  }
}
