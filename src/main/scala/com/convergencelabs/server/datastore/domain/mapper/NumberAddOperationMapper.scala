package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ops.NumberAddOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JNumber
import com.convergencelabs.server.domain.model.ot.ops.NumberAddOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument

object NumberAddOperationMapper {

  import NumberAddOperationFields._

  private[domain] implicit class NumberAddOperationToODocument(val s: NumberAddOperation) extends AnyVal {
    def asODocument: ODocument = numberAddOperationToODocument(s)
  }

  private[domain] implicit def numberAddOperationToODocument(obj: NumberAddOperation): ODocument = {
    val NumberAddOperation(path, noOp, delta) = obj
    val doc = new ODocument(NumberAddOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Delta, JValueMapper.jNumberToJava(delta))
    doc
  }

  private[domain] implicit class ODocumentToNumberAddOperation(val d: ODocument) extends AnyVal {
    def asNumberAddOperation: NumberAddOperation = oDocumentToNumberAddOperation(d)
  }

  private[domain] implicit def oDocumentToNumberAddOperation(doc: ODocument): NumberAddOperation = {
    assert(doc.getClassName == NumberAddOperationClassName)
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val delta = JValueMapper.javaToJValue(doc.field(Delta)).asInstanceOf[JNumber]
    NumberAddOperation(path.asScala.toList, noOp, delta)
  }

  private[domain] val NumberAddOperationClassName = "NumberAddOperation"

  private[domain] object NumberAddOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Delta = "delta"
  }
}