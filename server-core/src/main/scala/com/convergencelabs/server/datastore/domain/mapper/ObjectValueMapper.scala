package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import java.util.{ Map => JavaMap }
import scala.collection.JavaConverters._
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
import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue

object ObjectValueMapper extends ODocumentMapper {

  private[domain] implicit class ObjectValueToODocument(val obj: ObjectValue) extends AnyVal {
    def asODocument: ODocument = objectValueToODocument(obj)
  }

  private[domain] implicit def objectValueToODocument(obj: ObjectValue): ODocument = {
    val ObjectValue(vid, children) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.VID, vid)
    val docChildren = children map{case(k, v)=> (k, v.asODocument)}
    doc.field(Fields.Children, docChildren.asJava)
    doc
  }

  private[domain] implicit class ODocumentToObjectValue(val d: ODocument) extends AnyVal {
    def asObjectValue: ObjectValue = oDocumentToObjectValue(d)
  }

  private[domain] implicit def oDocumentToObjectValue(doc: ODocument): ObjectValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)

    val vid = doc.field(Fields.VID).asInstanceOf[String]
    val children: JavaMap[String, OIdentifiable] = doc.field(Fields.Children);
    val dataValues = children.asScala map {case (k, v) => (k, v.getRecord[ODocument].asDataValue)}
    
    ObjectValue(vid, dataValues.toMap)
  }

  private[domain] val DocumentClassName = "ObjectValue"
  private[domain] val OpDocumentClassName = "ObjectOpValue"

  private[domain] object Fields {
    val VID = "vid"
    val Children = "children"
  }
}
