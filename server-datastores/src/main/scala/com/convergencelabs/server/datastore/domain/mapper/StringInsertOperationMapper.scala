package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object StringInsertOperationMapper extends ODocumentMapper {

  private[domain] implicit class StringInsertOperationToODocument(val s: AppliedStringInsertOperation) extends AnyVal {
    def asODocument: ODocument = stringInsertOperationToODocument(s)
  }

  private[domain] implicit def stringInsertOperationToODocument(obj: AppliedStringInsertOperation): ODocument = {
    val AppliedStringInsertOperation(id, noOp, index, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.Val, value)
    doc
  }

  private[domain] implicit class ODocumentToStringInsertOperation(val d: ODocument) extends AnyVal {
    def asStringInsertOperation: AppliedStringInsertOperation = oDocumentToStringInsertOperation(d)
  }

  private[domain] implicit def oDocumentToStringInsertOperation(doc: ODocument): AppliedStringInsertOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val index = doc.field(Fields.Idx).asInstanceOf[Int]
    val value = doc.field(Fields.Val).asInstanceOf[String]
    AppliedStringInsertOperation(id, noOp, index, value)
  }

  private[domain] val DocumentClassName = "StringInsertOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
  }
}
