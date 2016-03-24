package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.JavaConversions._
import scala.language.implicitConversions
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.util.JValueMapper
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.ObjectValue
import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.db.record.ORecordLazyMap
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.convergencelabs.server.domain.model.data.DataValue

object ObjectValueMapper extends ODocumentMapper {

  private[domain] implicit class ObjectValueToODocument(val obj: ObjectValue) extends AnyVal {
    def asODocument: ODocument = objectValueToODocument(obj)
  }

  private[domain] implicit def objectValueToODocument(obj: ObjectValue): ODocument = {
    val ObjectValue(vid, children) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.VID, vid)
    val docChildren = children map{case(k, v)=> (k, v.asODocument)}
    doc.field(Fields.Children, docChildren)
    doc
  }

  private[domain] implicit class ODocumentToObjectValue(val d: ODocument) extends AnyVal {
    def asObjectValue: ObjectValue = oDocumentToObjectValue(d)
  }

  private[domain] implicit def oDocumentToObjectValue(doc: ODocument): ObjectValue = {
    validateDocumentClass(doc, DocumentClassName)

    val vid = doc.field(Fields.VID).asInstanceOf[String]
    val children: ORecordLazyMap = doc.field(Fields.Children);
    val dataValues = children.toMap map {case (k, v) => (k.toString() -> DataValueMapper.oDocumentToDataValue(v.getRecord()))}
    
    ObjectValue(vid, dataValues)
  }

  private[domain] val DocumentClassName = "ObjectValue"

  private[domain] object Fields {
    val VID = "vid"
    val Children = "children"
  }
}
