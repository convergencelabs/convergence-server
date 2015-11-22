package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import org.json4s.JsonAST.JObject

object ObjectSetOperationMapper {

  import ObjectSetOperationFields._

  private[domain] implicit class ObjectSetOperationToODocument(val s: ObjectSetOperation) extends AnyVal {
    def asODocument: ODocument = objectSetOperationToODocument(s)
  }

  private[domain] implicit def objectSetOperationToODocument(obj: ObjectSetOperation): ODocument = {
    val ObjectSetOperation(path, noOp, value) = obj
    val doc = new ODocument(ObjectSetOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Val, JValueMapper.jValueToJava(value))
    doc
  }

  private[domain] implicit class ODocumentToObjectSetOperation(val d: ODocument) extends AnyVal {
    def asObjectSetOperation: ObjectSetOperation = oDocumentToObjectSetOperation(d)
  }

  private[domain] implicit def oDocumentToObjectSetOperation(doc: ODocument): ObjectSetOperation = {
    assert(doc.getClassName == ObjectSetOperationClassName)
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val value = JValueMapper.javaToJValue(doc.field(Val)).asInstanceOf[JObject]
    ObjectSetOperation(path.asScala.toList, noOp, value)
  }

  private[domain] val ObjectSetOperationClassName = "ObjectSetOperation"

  private[domain] object ObjectSetOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
  }
}