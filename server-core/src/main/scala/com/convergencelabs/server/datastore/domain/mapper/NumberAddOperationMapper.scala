package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object NumberAddOperationMapper extends ODocumentMapper {

  private[domain] implicit class NumberAddOperationToODocument(val s: NumberAddOperation) extends AnyVal {
    def asODocument: ODocument = numberAddOperationToODocument(s)
  }

  private[domain] implicit def numberAddOperationToODocument(obj: NumberAddOperation): ODocument = {
    val NumberAddOperation(id, noOp, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Val, value)
    doc
  }

  private[domain] implicit class ODocumentToNumberAddOperation(val d: ODocument) extends AnyVal {
    def asNumberAddOperation: NumberAddOperation = oDocumentToNumberAddOperation(d)
  }

  private[domain] implicit def oDocumentToNumberAddOperation(doc: ODocument): NumberAddOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[Double]
    NumberAddOperation(id, noOp, value)
  }

  private[domain] val DocumentClassName = "NumberAddOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
  }
}
