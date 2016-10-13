package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object ArrayMoveOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArrayMoveOperationToODocument(val s: AppliedArrayMoveOperation) extends AnyVal {
    def asODocument: ODocument = arrayMoveOperationToODocument(s)
  }

  private[domain] implicit def arrayMoveOperationToODocument(obj: AppliedArrayMoveOperation): ODocument = {
    val AppliedArrayMoveOperation(id, noOp, from, to) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.From, from)
    doc.field(Fields.To, to)
    doc
  }

  private[domain] implicit class ODocumentToArrayMoveOperation(val d: ODocument) extends AnyVal {
    def asArrayMoveOperation: AppliedArrayMoveOperation = oDocumentToArrayMoveOperation(d)
  }

  private[domain] implicit def oDocumentToArrayMoveOperation(doc: ODocument): AppliedArrayMoveOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val from = doc.field(Fields.From).asInstanceOf[Int]
    val to = doc.field(Fields.To).asInstanceOf[Int]
    AppliedArrayMoveOperation(id, noOp, from, to)
  }

  private[domain] val DocumentClassName = "ArrayMoveOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val From = "fromIdx"
    val To = "toIdx"
  }
}
