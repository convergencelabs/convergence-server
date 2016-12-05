package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.orientechnologies.orient.core.record.impl.ODocument

object DoubleValueMapper extends ODocumentMapper {

  private[domain] implicit class DoubleValueToODocument(val obj: DoubleValue) extends AnyVal {
    def asODocument: ODocument = doubleValueToODocument(obj)
  }

  private[domain] implicit def doubleValueToODocument(obj: DoubleValue): ODocument = {
    val DoubleValue(id, value) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.Value, value)
    doc
  }

  private[domain] implicit class ODocumentToDoubleValue(val d: ODocument) extends AnyVal {
    def asDoubleValue: DoubleValue = oDocumentToDoubleValue(d)
  }

  private[domain] implicit def oDocumentToDoubleValue(doc: ODocument): DoubleValue = {
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
