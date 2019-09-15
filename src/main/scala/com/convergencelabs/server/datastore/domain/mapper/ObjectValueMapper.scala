package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ Map => JavaMap }

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.record.impl.ODocument

import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue
import com.orientechnologies.orient.core.sql.executor.OResult
import com.orientechnologies.orient.core.id.ORecordId

object ObjectValueMapper extends ODocumentMapper {

  private[domain] implicit class ObjectValueToODocument(val obj: ObjectValue) extends AnyVal {
    def asODocument: ODocument = objectValueToODocument(obj)
  }

  private[domain] implicit def objectValueToODocument(obj: ObjectValue): ODocument = {
    val ObjectValue(id, children) = obj
    val doc = new ODocument(OpDocumentClassName)
    doc.field(Fields.Id, id)
    val docChildren = children map { case (k, v) => (k, v.asODocument) }
    doc.field(Fields.Children, docChildren.asJava)
    doc
  }

  private[domain] implicit class ODocumentToObjectValue(val d: ODocument) extends AnyVal {
    def asObjectValue: ObjectValue = oDocumentToObjectValue(d)
  }

  private[domain] implicit def oDocumentToObjectValue(doc: ODocument): ObjectValue = {
    validateDocumentClass(doc, DocumentClassName, OpDocumentClassName)

    val id = doc.getProperty(Fields.Id).asInstanceOf[String]
    val children: JavaMap[String, Any] = doc.getProperty(Fields.Children)
    val dataValues = children.asScala map {
      case (k, v) =>
        if (v.isInstanceOf[OResult]) {
          (k, v.asInstanceOf[OResult].toElement.asInstanceOf[ODocument].asDataValue)
        } else if (v.isInstanceOf[ORecordId]) {
          (k, v.asInstanceOf[ORecordId].getRecord.asInstanceOf[ODocument].asDataValue)
        } else {
          (k, v.asInstanceOf[ODocument].asDataValue)
        }
    }

    ObjectValue(id, dataValues.toMap)
  }

  private[domain] val DocumentClassName = "ObjectValue"
  private[domain] val OpDocumentClassName = "ObjectOpValue"

  private[domain] object Fields {
    val Id = "id"
    val Children = "children"
  }
}
