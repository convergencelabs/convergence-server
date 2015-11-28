package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JNumber

object NumberSetOperationMapper {

  import NumberSetOperationFields._

  private[domain] implicit class NumberSetOperationToODocument(val s: NumberSetOperation) extends AnyVal {
    def asODocument: ODocument = numberSetOperationToODocument(s)
  }

  private[domain] implicit def numberSetOperationToODocument(obj: NumberSetOperation): ODocument = {
    val NumberSetOperation(path, noOp, value) = obj
    val doc = new ODocument(NumberSetOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Val, JValueMapper.jNumberToJava(value))
    doc
  }

  private[domain] implicit class ODocumentToNumberSetOperation(val d: ODocument) extends AnyVal {
    def asNumberSetOperation: NumberSetOperation = oDocumentToNumberSetOperation(d)
  }

  private[domain] implicit def oDocumentToNumberSetOperation(doc: ODocument): NumberSetOperation = {
    if (doc.getClassName != NumberSetOperationClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${NumberSetOperationClassName}': ${doc.getClassName}")
    }
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val value = JValueMapper.javaToJValue(doc.field(Val)).asInstanceOf[JNumber]
    NumberSetOperation(path.asScala.toList, noOp, value)
  }

  private[domain] val NumberSetOperationClassName = "NumberSetOperation"

  private[domain] object NumberSetOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
  }
}