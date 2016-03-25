package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import org.json4s.JsonAST.JObject
import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object ObjectSetPropertyOperationMapper extends ODocumentMapper {

  private[domain] implicit class ObjectSetPropertyOperationToODocument(val s: ObjectSetPropertyOperation) extends AnyVal {
    def asODocument: ODocument = objectSetPropertyOperationToODocument(s)
  }

  private[domain] implicit def objectSetPropertyOperationToODocument(obj: ObjectSetPropertyOperation): ODocument = {
    val ObjectSetPropertyOperation(id, noOp, prop, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Prop, prop)
    doc.field(Fields.Val, value.asODocument)
    doc
  }

  private[domain] implicit class ODocumentToObjectSetPropertyOperation(val d: ODocument) extends AnyVal {
    def asObjectSetPropertyOperation: ObjectSetPropertyOperation = oDocumentToObjectSetPropertyOperation(d)
  }

  private[domain] implicit def oDocumentToObjectSetPropertyOperation(doc: ODocument): ObjectSetPropertyOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val prop = doc.field(Fields.Prop).asInstanceOf[String]
    val value = doc.field(Fields.Val).asInstanceOf[ODocument].asDataValue
    ObjectSetPropertyOperation(id, noOp, prop, value)
  }

  private[domain] val DocumentClassName = "ObjectSetPropertyOperation"

  private[domain] object Fields {
    val Id = "id"
    val NoOp = "noOp"
    val Prop = "prop"
    val Val = "val"
  }
}
