package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.util.JValueMapper
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.data.ObjectValue

object ObjectValueMapper extends ODocumentMapper {

  private[domain] implicit class ObjectValueToODocument(val s: ObjectValue) extends AnyVal {
    def asODocument: ODocument = ObjectValueToODocument(s)
  }

  private[domain] implicit def ObjectValueToODocument(obj: ObjectValue): ODocument = {
    val ObjectValue(path, noOp, index, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Path, path.asJava)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.Val, JValueMapper.jValueToJava(value))
    doc
  }

  private[domain] implicit class ODocumentToObjectValue(val d: ODocument) extends AnyVal {
    def asObjectValue: ObjectValue = oDocumentToObjectValue(d)
  }

  private[domain] implicit def oDocumentToObjectValue(doc: ODocument): ObjectValue = {
    validateDocumentClass(doc, DocumentClassName)

    val path = doc.field(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Fields.Idx).asInstanceOf[Int]
    val value = JValueMapper.javaToJValue(doc.field(Fields.Val))
    ObjectValue(path.asScala.toList, noOp, idx, value)
  }

  private[domain] val DocumentClassName = "ObjectValue"

  private[domain] object Fields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
  }
}
