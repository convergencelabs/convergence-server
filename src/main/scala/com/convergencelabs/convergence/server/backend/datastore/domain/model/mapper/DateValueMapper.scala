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

import java.util.Date

import com.convergencelabs.convergence.server.backend.datastore.ODocumentMapper
import com.convergencelabs.convergence.server.model.domain.model.DateValue
import com.orientechnologies.orient.core.record.impl.ODocument

import scala.language.implicitConversions

object DateValueMapper extends ODocumentMapper {

  private[domain] implicit class DateValueToODocument(val obj: DateValue) extends AnyVal {
    def asODocument: ODocument = dateValueToODocument(obj)
  }

  private[domain] implicit def dateValueToODocument(obj: DateValue): ODocument = {
    val DateValue(id, value) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.Value, Date.from(value))
    doc
  }

  private[domain] implicit class ODocumentToDateValue(val d: ODocument) extends AnyVal {
    def asDateValue: DateValue = oDocumentToDateValue(d)
  }

  private[domain] implicit def oDocumentToDateValue(doc: ODocument): DateValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val value = doc.field(Fields.Value).asInstanceOf[Date]
    DateValue(id, value.toInstant());
  }

  private[domain] val DocumentClassName = "DateValue"
  private[domain] val OpDocumentClassName = "DateOpValue"

  private[domain] object Fields {
    val Id = "id"
    val Value = "value"
  }
}
