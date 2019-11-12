/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.StringValue
import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue

object StringValueMapper extends ODocumentMapper {

  private[domain] implicit class StringValueToODocument(val obj: StringValue) extends AnyVal {
    def asODocument: ODocument = stringValueToODocument(obj)
  }

  private[domain] implicit def stringValueToODocument(obj: StringValue): ODocument = {
    val StringValue(id, value) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.Value, value)
    doc
  }

  private[domain] implicit class ODocumentToStringValue(val d: ODocument) extends AnyVal {
    def asStringValue: StringValue = oDocumentToStringValue(d)
  }

  private[domain] implicit def oDocumentToStringValue(doc: ODocument): StringValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val value = doc.field(Fields.Value).asInstanceOf[String]
    StringValue(id, value);
  }

  private[domain] val DocumentClassName = "StringValue"
  private[domain] val OpDocumentClassName = "StringOpValue"

  private[domain] object Fields {
    val Id = "id"
    val Value = "value"
  }
}
