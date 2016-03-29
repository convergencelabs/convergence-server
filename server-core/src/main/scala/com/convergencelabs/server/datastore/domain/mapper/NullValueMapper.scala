package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.util.JValueMapper
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.NullValue
import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue

object NullValueMapper extends ODocumentMapper {

  private[domain] implicit class NullValueToODocument(val value: NullValue) extends AnyVal {
    def asODocument: ODocument = nullValueToODocument(value)
  }

  private[domain] implicit def nullValueToODocument(value: NullValue): ODocument = {
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.VID, value.id)
    doc
  }

  private[domain] implicit class ODocumentToNullValue(val d: ODocument) extends AnyVal {
    def asNullValue: NullValue = oDocumentToNullValue(d)
  }

  private[domain] implicit def oDocumentToNullValue(doc: ODocument): NullValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)
    val vid = doc.field(Fields.VID).asInstanceOf[String]
    NullValue(vid);
  }

  private[domain] val DocumentClassName = "NullValue"
  private[domain] val OpDocumentClassName = "NullOpValue"

  private[domain] object Fields {
    val VID = "vid"
  }
}
