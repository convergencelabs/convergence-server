package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object BooleanSetOperationMapper extends ODocumentMapper {

  private[domain] implicit class BooleanSetOperationToODocument(val s: AppliedBooleanSetOperation) extends AnyVal {
    def asODocument: ODocument = numberSetOperationToODocument(s)
  }

  private[domain] implicit def numberSetOperationToODocument(op: AppliedBooleanSetOperation): ODocument = {
    val AppliedBooleanSetOperation(id, noOp, value, oldValue) = op
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Val, value)
    doc.field(Fields.OldVal, oldValue.getOrElse(null))
    doc
  }

  private[domain] implicit class ODocumentToBooleanSetOperation(val d: ODocument) extends AnyVal {
    def asBooleanSetOperation: AppliedBooleanSetOperation = oDocumentToBooleanSetOperation(d)
  }

  private[domain] implicit def oDocumentToBooleanSetOperation(doc: ODocument): AppliedBooleanSetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[Boolean]
    val oldValue = doc.field(Fields.OldVal).asInstanceOf[Boolean]
    AppliedBooleanSetOperation(id, noOp, value, Option(oldValue))
  }

  private[domain] val DocumentClassName = "BooleanSetOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
    val OldVal = "oldValue"
  }
}
