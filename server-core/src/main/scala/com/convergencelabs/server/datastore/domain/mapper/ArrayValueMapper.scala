package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.record.impl.ODocument

import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue

object ArrayValueMapper extends ODocumentMapper {

  private[domain] implicit class ArrayValueToODocument(val obj: ArrayValue) extends AnyVal {
    def asODocument: ODocument = arrayValueToODocument(obj)
  }

  private[domain] implicit def arrayValueToODocument(obj: ArrayValue): ODocument = {
    val ArrayValue(id, children) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.Id, id)
    val docChildren = children map{v => v.asODocument}
    doc.field(Fields.Children, docChildren.asJava)
    doc
  }

  private[domain] implicit class ODocumentToArrayValue(val d: ODocument) extends AnyVal {
    def asArrayValue: ArrayValue = oDocumentToArrayValue(d)
  }

  private[domain] implicit def oDocumentToArrayValue(doc: ODocument): ArrayValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val children: JavaList[OIdentifiable] = doc.field(Fields.Children);
    val dataValues = children.asScala map {v => v.getRecord[ODocument].asDataValue}
    ArrayValue(id, dataValues.toList)
  }

  private[domain] val DocumentClassName = "ArrayValue"
  private[domain] val OpDocumentClassName = "ArrayOpValue"


  private[domain] object Fields {
    val Id = "id"
    val Children = "children"
  }
}
