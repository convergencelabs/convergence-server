package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import org.json4s.JsonAST.JObject

object ObjectAddPropertyOperationMapper {

  import ObjectAddPropertyOperationFields._

  private[domain] implicit class ObjectAddPropertyOperationToODocument(val s: ObjectAddPropertyOperation) extends AnyVal {
    def asODocument: ODocument = objectAddPropertyOperationToODocument(s)
  }

  private[domain] implicit def objectAddPropertyOperationToODocument(obj: ObjectAddPropertyOperation): ODocument = {
    val ObjectAddPropertyOperation(path, noOp, prop, value) = obj
    val doc = new ODocument(ObjectAddPropertyOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Prop, prop)
    doc.field(Val, JValueMapper.jValueToJava(value))
    doc
  }

  private[domain] implicit class ODocumentToObjectAddPropertyOperation(val d: ODocument) extends AnyVal {
    def asObjectAddPropertyOperation: ObjectAddPropertyOperation = oDocumentToObjectAddPropertyOperation(d)
  }

  private[domain] implicit def oDocumentToObjectAddPropertyOperation(doc: ODocument): ObjectAddPropertyOperation = {
    assert(doc.getClassName == ObjectAddPropertyOperationClassName)
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val prop = doc.field(Prop).asInstanceOf[String]
    val value = JValueMapper.javaToJValue(doc.field(Val)) 
    ObjectAddPropertyOperation(path.asScala.toList, noOp, prop, value)
  }

  private[domain] val ObjectAddPropertyOperationClassName = "ObjectAddPropertyOperation"

  private[domain] object ObjectAddPropertyOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Prop = "prop"
    val Val = "val"
  }
}