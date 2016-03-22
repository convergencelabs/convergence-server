package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JNumber
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import org.json4s.JsonAST.JDouble

object NumberAddOperationMapper extends ODocumentMapper {

  private[domain] implicit class NumberAddOperationToODocument(val s: NumberAddOperation) extends AnyVal {
    def asODocument: ODocument = numberAddOperationToODocument(s)
  }

  private[domain] implicit def numberAddOperationToODocument(obj: NumberAddOperation): ODocument = {
    val NumberAddOperation(id, noOp, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Val, JValueMapper.jNumberToJava(value))
    doc
  }

  private[domain] implicit class ODocumentToNumberAddOperation(val d: ODocument) extends AnyVal {
    def asNumberAddOperation: NumberAddOperation = oDocumentToNumberAddOperation(d)
  }

  private[domain] implicit def oDocumentToNumberAddOperation(doc: ODocument): NumberAddOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = JValueMapper.javaToJValue(doc.field(Fields.Val)).asInstanceOf[JDouble]
    NumberAddOperation(id, noOp, value)
  }

  private[domain] val DocumentClassName = "NumberAddOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
  }
}
