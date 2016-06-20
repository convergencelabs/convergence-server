package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object ArrayRemoveOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArrayRemoveOperationToODocument(val s: ArrayRemoveOperation) extends AnyVal {
    def asODocument: ODocument = arrayRemoveOperationToODocument(s)
  }

  private[domain] implicit def arrayRemoveOperationToODocument(obj: ArrayRemoveOperation): ODocument = {
    val ArrayRemoveOperation(id, noOp, index) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc
  }

  private[domain] implicit class ODocumentToArrayRemoveOperation(val d: ODocument) extends AnyVal {
    def asArrayRemoveOperation: ArrayRemoveOperation = oDocumentToArrayRemoveOperation(d)
  }

  private[domain] implicit def oDocumentToArrayRemoveOperation(doc: ODocument): ArrayRemoveOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Fields.Idx).asInstanceOf[Int]
    ArrayRemoveOperation(id, noOp, idx)
  }

  private[domain] val DocumentClassName = "ArrayRemoveOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Idx = "idx"
  }
}
