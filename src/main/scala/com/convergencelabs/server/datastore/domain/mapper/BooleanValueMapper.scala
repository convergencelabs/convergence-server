/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.orientechnologies.orient.core.record.impl.ODocument

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
