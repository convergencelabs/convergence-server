package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue
import com.orientechnologies.orient.core.metadata.schema.OType

object ArrayInsertOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArrayInsertOperationToODocument(val s: AppliedArrayInsertOperation) extends AnyVal {
    def asODocument: ODocument = arrayInsertOperationToODocument(s)
  }

  private[domain] implicit def arrayInsertOperationToODocument(obj: AppliedArrayInsertOperation): ODocument = {
    val AppliedArrayInsertOperation(id, noOp, index, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.Val, value.asODocument, OType.EMBEDDED)
    doc
  }

  private[domain] implicit class ODocumentToArrayInsertOperation(val d: ODocument) extends AnyVal {
    def asArrayInsertOperation: AppliedArrayInsertOperation = oDocumentToArrayInsertOperation(d)
  }

  private[domain] implicit def oDocumentToArrayInsertOperation(doc: ODocument): AppliedArrayInsertOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Fields.Idx).asInstanceOf[Int]
    val value = doc.field(Fields.Val).asInstanceOf[ODocument].asDataValue
    AppliedArrayInsertOperation(id, noOp, idx, value)
  }

  private[domain] val DocumentClassName = "ArrayInsertOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
  }
}
