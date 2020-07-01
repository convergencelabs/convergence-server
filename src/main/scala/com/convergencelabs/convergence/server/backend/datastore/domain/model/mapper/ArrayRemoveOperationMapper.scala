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
import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DataValueMapper.{DataValueToODocument, ODocumentToDataValue}
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedArrayRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.language.implicitConversions

object ArrayRemoveOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArrayRemoveOperationToODocument(val s: AppliedArrayRemoveOperation) extends AnyVal {
    def asODocument: ODocument = arrayRemoveOperationToODocument(s)
  }

  private[domain] implicit def arrayRemoveOperationToODocument(obj: AppliedArrayRemoveOperation): ODocument = {
    val AppliedArrayRemoveOperation(id, noOp, index, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    val oldValDoc = (oldValue map {_.asODocument})
    doc.field(Fields.OldValue, oldValDoc.getOrElse(null))
    doc
  }

  private[domain] implicit class ODocumentToArrayRemoveOperation(val d: ODocument) extends AnyVal {
    def asArrayRemoveOperation: AppliedArrayRemoveOperation = oDocumentToArrayRemoveOperation(d)
  }

  private[domain] implicit def oDocumentToArrayRemoveOperation(doc: ODocument): AppliedArrayRemoveOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Fields.Idx).asInstanceOf[Int]
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[ODocument]) map {_.asDataValue}
    AppliedArrayRemoveOperation(id, noOp, idx, oldValue)
  }

  private[domain] val DocumentClassName = "ArrayRemoveOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Idx = "idx"
    val OldValue = "oldVal"
  }
}
