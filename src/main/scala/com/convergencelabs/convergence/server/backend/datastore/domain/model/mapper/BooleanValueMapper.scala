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
import com.convergencelabs.convergence.server.domain.model.data.BooleanValue
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.language.implicitConversions

object BooleanValueMapper extends ODocumentMapper {

  private[domain] implicit class BooleanValueToODocument(val obj: BooleanValue) extends AnyVal {
    def asODocument: ODocument = booleanValueToODocument(obj)
  }

  private[domain] implicit def booleanValueToODocument(obj: BooleanValue): ODocument = {
    val BooleanValue(id, value) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.Value, value)
    doc
  }

  private[domain] implicit class ODocumentToBooleanValue(val d: ODocument) extends AnyVal {
    def asBooleanValue: BooleanValue = oDocumentToBooleanValue(d)
  }

  private[domain] implicit def oDocumentToBooleanValue(doc: ODocument): BooleanValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val value = doc.field(Fields.Value).asInstanceOf[Boolean]
    BooleanValue(id, value);
  }

  private[domain] val DocumentClassName = "BooleanValue"
  private[domain] val OpDocumentClassName = "BooleanOpValue"

  private[domain] object Fields {
    val Id = "id"
    val Value = "value"
  }
}
