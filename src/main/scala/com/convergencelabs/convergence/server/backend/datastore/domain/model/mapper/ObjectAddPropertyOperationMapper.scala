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

import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DataValueMapper.{DataValueToODocument, ODocumentToDataValue}
import com.convergencelabs.convergence.server.backend.datastore.mapper.ODocumentMapper
import com.convergencelabs.convergence.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.language.implicitConversions

object ObjectAddPropertyOperationMapper extends ODocumentMapper {

  private[domain] implicit class ObjectAddPropertyOperationToODocument(val s: AppliedObjectAddPropertyOperation) extends AnyVal {
    def asODocument: ODocument = objectAddPropertyOperationToODocument(s)
  }

  private[domain] implicit def objectAddPropertyOperationToODocument(obj: AppliedObjectAddPropertyOperation): ODocument = {
    val AppliedObjectAddPropertyOperation(id, noOp, prop, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Prop, prop)
    doc.field(Fields.Val, value.asODocument, OType.EMBEDDED)
    doc
  }

  private[domain] implicit class ODocumentToObjectAddPropertyOperation(val d: ODocument) extends AnyVal {
    def asObjectAddPropertyOperation: AppliedObjectAddPropertyOperation = oDocumentToObjectAddPropertyOperation(d)
  }

  private[domain] implicit def oDocumentToObjectAddPropertyOperation(doc: ODocument): AppliedObjectAddPropertyOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val prop = doc.field(Fields.Prop).asInstanceOf[String]
    val value = doc.field(Fields.Val).asInstanceOf[ODocument].asDataValue
    AppliedObjectAddPropertyOperation(id, noOp, prop, value)
  }

  private[domain] val DocumentClassName = "ObjectAddPropertyOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Prop = "prop"
    val Val = "val"
  }
}
