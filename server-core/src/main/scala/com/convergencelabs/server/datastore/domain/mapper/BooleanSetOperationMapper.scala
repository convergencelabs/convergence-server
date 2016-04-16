package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object BooleanSetOperationMapper extends ODocumentMapper {

  private[domain] implicit class BooleanSetOperationToODocument(val s: BooleanSetOperation) extends AnyVal {
    def asODocument: ODocument = numberSetOperationToODocument(s)
  }

  private[domain] implicit def numberSetOperationToODocument(op: BooleanSetOperation): ODocument = {
    val BooleanSetOperation(id, noOp, value) = op
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Val, value)
    doc
  }

  private[domain] implicit class ODocumentToBooleanSetOperation(val d: ODocument) extends AnyVal {
    def asBooleanSetOperation: BooleanSetOperation = oDocumentToBooleanSetOperation(d)
  }

  private[domain] implicit def oDocumentToBooleanSetOperation(doc: ODocument): BooleanSetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[Boolean]
    BooleanSetOperation(id, noOp, value)
  }

  private[domain] val DocumentClassName = "BooleanSetOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
  }
}
