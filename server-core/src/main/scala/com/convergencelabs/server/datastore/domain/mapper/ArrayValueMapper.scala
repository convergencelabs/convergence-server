package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.JavaConversions._
import scala.language.implicitConversions
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.util.JValueMapper
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.ArrayValue
import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue
import com.orientechnologies.orient.core.db.record.ORecordLazyList
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.db.record.OIdentifiable

object ArrayValueMapper extends ODocumentMapper {

  private[domain] implicit class ArrayValueToODocument(val obj: ArrayValue) extends AnyVal {
    def asODocument: ODocument = arrayValueToODocument(obj)
  }

  private[domain] implicit def arrayValueToODocument(obj: ArrayValue): ODocument = {
    val ArrayValue(vid, children) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.VID, vid)
    val docChildren = children map{v => v.asODocument}
    doc.field(Fields.Children, docChildren.asJava)
    doc
  }

  private[domain] implicit class ODocumentToArrayValue(val d: ODocument) extends AnyVal {
    def asArrayValue: ArrayValue = oDocumentToArrayValue(d)
  }

  private[domain] implicit def oDocumentToArrayValue(doc: ODocument): ArrayValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)

    val vid = doc.field(Fields.VID).asInstanceOf[String]
    val children: JavaList[OIdentifiable] = doc.field(Fields.Children);
    val dataValues = children map {v => v.getRecord[ODocument].asDataValue}
    ArrayValue(vid, dataValues.toList)
  }

  private[domain] val DocumentClassName = "ArrayValue"
  private[domain] val OpDocumentClassName = "ArrayOpValue"


  private[domain] object Fields {
    val VID = "vid"
    val Children = "children"
  }
}
