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
    val StringValue(vid, value) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.VID, vid)
    doc.field(Fields.Value, value)
    doc
  }

  private[domain] implicit class ODocumentToStringValue(val d: ODocument) extends AnyVal {
    def asStringValue: StringValue = oDocumentToStringValue(d)
  }

  private[domain] implicit def oDocumentToStringValue(doc: ODocument): StringValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)

    val vid = doc.field(Fields.VID).asInstanceOf[String]
    val value = doc.field(Fields.Value).asInstanceOf[String]
    StringValue(vid, value);
  }

  private[domain] val DocumentClassName = "StringValue"
  private[domain] val OpDocumentClassName = "StringOpValue"

  private[domain] object Fields {
    val VID = "vid"
    val Value = "value"
  }
}
