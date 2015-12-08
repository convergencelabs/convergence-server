package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import org.json4s.JsonAST.JObject
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object ObjectSetOperationMapper extends ODocumentMapper {

  private[domain] implicit class ObjectSetOperationToODocument(val s: ObjectSetOperation) extends AnyVal {
    def asODocument: ODocument = objectSetOperationToODocument(s)
  }

  private[domain] implicit def objectSetOperationToODocument(obj: ObjectSetOperation): ODocument = {
    val ObjectSetOperation(path, noOp, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Path, path.asJava)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Val, JValueMapper.jValueToJava(value))
    doc
  }

  private[domain] implicit class ODocumentToObjectSetOperation(val d: ODocument) extends AnyVal {
    def asObjectSetOperation: ObjectSetOperation = oDocumentToObjectSetOperation(d)
  }

  private[domain] implicit def oDocumentToObjectSetOperation(doc: ODocument): ObjectSetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val path = doc.field(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = JValueMapper.javaToJValue(doc.field(Fields.Val)).asInstanceOf[JObject]
    ObjectSetOperation(path.asScala.toList, noOp, value)
  }

  private[domain] val DocumentClassName = "ObjectSetOperation"

  private[domain] object Fields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
  }
}
