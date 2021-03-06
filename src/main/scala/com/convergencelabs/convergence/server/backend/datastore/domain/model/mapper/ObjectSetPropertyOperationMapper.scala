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
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedObjectSetPropertyOperation
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.language.implicitConversions

object ObjectSetPropertyOperationMapper extends ODocumentMapper {

  private[domain] implicit class ObjectSetPropertyOperationToODocument(val s: AppliedObjectSetPropertyOperation) extends AnyVal {
    def asODocument: ODocument = objectSetPropertyOperationToODocument(s)
  }

  private[domain] implicit def objectSetPropertyOperationToODocument(obj: AppliedObjectSetPropertyOperation): ODocument = {
    val AppliedObjectSetPropertyOperation(id, noOp, prop, value, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Prop, prop)
    doc.field(Fields.Val, dataValueToODocument(value), OType.EMBEDDED)
    val oldValDoc = oldValue.map(dataValueToODocument)
    doc.field(Fields.OldValue, oldValDoc.orNull)
    doc
  }

  private[domain] implicit class ODocumentToObjectSetPropertyOperation(val d: ODocument) extends AnyVal {
    def asObjectSetPropertyOperation: AppliedObjectSetPropertyOperation = oDocumentToObjectSetPropertyOperation(d)
  }

  private[domain] implicit def oDocumentToObjectSetPropertyOperation(doc: ODocument): AppliedObjectSetPropertyOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val prop = doc.field(Fields.Prop).asInstanceOf[String]
    val value = oDocumentToDataValue(doc.field(Fields.Val).asInstanceOf[ODocument])
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[ODocument]).map(oDocumentToDataValue)
    AppliedObjectSetPropertyOperation(id, noOp, prop, value, oldValue)
  }

  private[domain] val DocumentClassName = "ObjectSetPropertyOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Prop = "prop"
    val Val = "val"
    val OldValue = "oldVal"
  }
}
