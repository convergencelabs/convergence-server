package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.util.JValueMapper
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.DoubleValue
import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue

object DoubleValueMapper extends ODocumentMapper {

  private[domain] implicit class DoubleValueToODocument(val obj: DoubleValue) extends AnyVal {
    def asODocument: ODocument = doubleValueToODocument(obj)
  }

  private[domain] implicit def doubleValueToODocument(obj: DoubleValue): ODocument = {
    val DoubleValue(vid, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.VID, vid)
    doc.field(Fields.Value, value)
    doc
  }

  private[domain] implicit class ODocumentToDoubleValue(val d: ODocument) extends AnyVal {
    def asDoubleValue: DoubleValue = oDocumentToDoubleValue(d)
  }

  private[domain] implicit def oDocumentToDoubleValue(doc: ODocument): DoubleValue = {
    validateDocumentClass(doc, DocumentClassName)

    val vid = doc.field(Fields.VID).asInstanceOf[String]
    val value = doc.field(Fields.Value).asInstanceOf[Double]
    DoubleValue(vid, value);
  }

  private[domain] val DocumentClassName = "DoubleOpValue"

  private[domain] object Fields {
    val VID = "vid"
    val Value = "value"
  }
}
