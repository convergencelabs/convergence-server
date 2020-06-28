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

package com.convergencelabs.convergence.server.datastore.domain.mapper

import com.convergencelabs.convergence.server.datastore.domain.mapper.DataValueMapper.{DataValueToODocument, ODocumentToDataValue}
import com.convergencelabs.convergence.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.convergence.server.domain.model.ot.AppliedObjectSetPropertyOperation
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
    doc.field(Fields.Val, value.asODocument, OType.EMBEDDED)
    val oldValDoc = (oldValue map {_.asODocument})
    doc.field(Fields.OldValue, oldValDoc.getOrElse(null))
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
    val value = doc.field(Fields.Val).asInstanceOf[ODocument].asDataValue
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[ODocument]) map {_.asDataValue}
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
