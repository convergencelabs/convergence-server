package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import org.json4s.JsonAST.JObject

object ObjectSetPropertyOperationMapper {

  import ObjectSetPropertyOperationFields._

  private[domain] implicit class ObjectSetPropertyOperationToODocument(val s: ObjectSetPropertyOperation) extends AnyVal {
    def asODocument: ODocument = objectSetPropertyOperationToODocument(s)
  }

  private[domain] implicit def objectSetPropertyOperationToODocument(obj: ObjectSetPropertyOperation): ODocument = {
    val ObjectSetPropertyOperation(path, noOp, prop, value) = obj
    val doc = new ODocument(ObjectSetPropertyOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Prop, prop)
    doc.field(Val, JValueMapper.jValueToJava(value))
    doc
  }

  private[domain] implicit class ODocumentToObjectSetPropertyOperation(val d: ODocument) extends AnyVal {
    def asObjectSetPropertyOperation: ObjectSetPropertyOperation = oDocumentToObjectSetPropertyOperation(d)
  }

  private[domain] implicit def oDocumentToObjectSetPropertyOperation(doc: ODocument): ObjectSetPropertyOperation = {
    assert(doc.getClassName == ObjectSetPropertyOperationClassName)
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val prop = doc.field(Prop).asInstanceOf[String]
    val value = JValueMapper.javaToJValue(doc.field(Val)) 
    ObjectSetPropertyOperation(path.asScala.toList, noOp, prop, value)
  }

  private[domain] val ObjectSetPropertyOperationClassName = "ObjectSetPropertyOperation"

  private[domain] object ObjectSetPropertyOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Prop = "prop"
    val Val = "val"
  }
}