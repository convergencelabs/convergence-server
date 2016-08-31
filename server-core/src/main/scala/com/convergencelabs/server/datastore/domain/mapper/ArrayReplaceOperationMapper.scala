package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue
import com.orientechnologies.orient.core.metadata.schema.OType

object ArrayReplaceOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArrayReplaceOperationToODocument(val s: AppliedArrayReplaceOperation) extends AnyVal {
    def asODocument: ODocument = arrayReplaceOperationToODocument(s)
  }

  private[domain] implicit def arrayReplaceOperationToODocument(obj: AppliedArrayReplaceOperation): ODocument = {
    val AppliedArrayReplaceOperation(id, noOp, index, value, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.Val, value.asODocument, OType.EMBEDDED)
    val oldValDoc = (oldValue map {_.asODocument})
    doc.field(Fields.OldValue, oldValDoc.getOrElse(null))
    doc
  }

  private[domain] implicit class ODocumentToArrayReplaceOperation(val d: ODocument) extends AnyVal {
    def asArrayReplaceOperation: AppliedArrayReplaceOperation = oDocumentToArrayReplaceOperation(d)
  }

  private[domain] implicit def oDocumentToArrayReplaceOperation(doc: ODocument): AppliedArrayReplaceOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Fields.Idx).asInstanceOf[Int]
    val value = doc.field(Fields.Val).asInstanceOf[ODocument].asDataValue
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[ODocument]) map {_.asDataValue}
    AppliedArrayReplaceOperation(id, noOp, idx, value, oldValue)
  }

  private[domain] val DocumentClassName = "ArrayReplaceOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
    val OldValue = "oldValue"
  }
}
