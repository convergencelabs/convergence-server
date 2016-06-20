package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ Map => JavaMap }

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue

object ObjectSetOperationMapper extends ODocumentMapper {

  private[domain] implicit class ObjectSetOperationToODocument(val s: ObjectSetOperation) extends AnyVal {
    def asODocument: ODocument = objectSetOperationToODocument(s)
  }

  private[domain] implicit def objectSetOperationToODocument(obj: ObjectSetOperation): ODocument = {
    val ObjectSetOperation(id, noOp, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    val valueDoc = obj.value map {case (k, v) => (k, v.asODocument)}
    doc.field(Fields.Val, valueDoc.asJava)
    doc
  }

  private[domain] implicit class ODocumentToObjectSetOperation(val d: ODocument) extends AnyVal {
    def asObjectSetOperation: ObjectSetOperation = oDocumentToObjectSetOperation(d)
  }

  private[domain] implicit def oDocumentToObjectSetOperation(doc: ODocument): ObjectSetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[JavaMap[String, ODocument]].asScala map {case (k, v) => (k, v.asDataValue)}
    ObjectSetOperation(id, noOp, value.toMap)
  }

  private[domain] val DocumentClassName = "ObjectSetOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
  }
}
