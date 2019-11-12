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

import scala.language.implicitConversions

import com.convergencelabs.convergence.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.convergence.server.domain.model.data.NullValue
import com.orientechnologies.orient.core.record.impl.ODocument

object NullValueMapper extends ODocumentMapper {

  private[domain] implicit class NullValueToODocument(val value: NullValue) extends AnyVal {
    def asODocument: ODocument = nullValueToODocument(value)
  }

  private[domain] implicit def nullValueToODocument(value: NullValue): ODocument = {
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.Id, value.id)
    doc
  }

  private[domain] implicit class ODocumentToNullValue(val d: ODocument) extends AnyVal {
    def asNullValue: NullValue = oDocumentToNullValue(d)
  }

  private[domain] implicit def oDocumentToNullValue(doc: ODocument): NullValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)
    val id = doc.field(Fields.Id).asInstanceOf[String]
    NullValue(id);
  }

  private[domain] val DocumentClassName = "NullValue"
  private[domain] val OpDocumentClassName = "NullOpValue"

  private[domain] object Fields {
    val Id = "id"
  }
}
