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
import com.convergencelabs.server.domain.model.data.DateValue
import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue
import java.util.Date

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
