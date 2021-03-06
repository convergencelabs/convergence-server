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
import com.convergencelabs.convergence.server.model.domain.model.DoubleValue
import com.orientechnologies.orient.core.record.impl.ODocument

object DoubleValueMapper extends ODocumentMapper {

  private[domain] def doubleValueToODocument(obj: DoubleValue): ODocument = {
    val DoubleValue(id, value) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.Value, value)
    doc
  }

  private[domain] def oDocumentToDoubleValue(doc: ODocument): DoubleValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val value = doc.field(Fields.Value).asInstanceOf[Double]
    DoubleValue(id, value);
  }

  private[domain] val DocumentClassName = "DoubleValue"
  private[domain] val OpDocumentClassName = "DoubleOpValue"

  private[domain] object Fields {
    val Id = "id"
    val Value = "value"
  }
}
