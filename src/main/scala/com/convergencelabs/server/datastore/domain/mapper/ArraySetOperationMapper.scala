package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import org.json4s.JsonAST.JArray

object ArraySetOperationMapper {

  import ArraySetOperationFields._

  private[domain] implicit class ArraySetOperationToODocument(val s: ArraySetOperation) extends AnyVal {
    def asODocument: ODocument = arraySetOperationToODocument(s)
  }

  private[domain] implicit def arraySetOperationToODocument(obj: ArraySetOperation): ODocument = {
    val ArraySetOperation(path, noOp, value) = obj
    val doc = new ODocument(ArraySetOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Val, JValueMapper.jValueToJava(value))
    doc
  }

  private[domain] implicit class ODocumentToArraySetOperation(val d: ODocument) extends AnyVal {
    def asArraySetOperation: ArraySetOperation = oDocumentToArraySetOperation(d)
  }

  private[domain] implicit def oDocumentToArraySetOperation(doc: ODocument): ArraySetOperation = {
    assert(doc.getClassName == ArraySetOperationClassName)
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val value = JValueMapper.javaToJValue(doc.field(Val)).asInstanceOf[JArray] 
    ArraySetOperation(path.asScala.toList, noOp, value)
  }

  private[domain] val ArraySetOperationClassName = "ArraySetOperation"

  private[domain] object ArraySetOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
  }
}