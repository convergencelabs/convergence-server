package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.domain.model.data.DataValue

import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue

object ArrayRemoveOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArrayRemoveOperationToODocument(val s: AppliedArrayRemoveOperation) extends AnyVal {
    def asODocument: ODocument = arrayRemoveOperationToODocument(s)
  }

  private[domain] implicit def arrayRemoveOperationToODocument(obj: AppliedArrayRemoveOperation): ODocument = {
    val AppliedArrayRemoveOperation(id, noOp, index, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    val oldValDoc = (oldValue map {_.asODocument})
    doc.field(Fields.OldValue, oldValDoc.getOrElse(null))
    doc
  }

  private[domain] implicit class ODocumentToArrayRemoveOperation(val d: ODocument) extends AnyVal {
    def asArrayRemoveOperation: AppliedArrayRemoveOperation = oDocumentToArrayRemoveOperation(d)
  }

  private[domain] implicit def oDocumentToArrayRemoveOperation(doc: ODocument): AppliedArrayRemoveOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Fields.Idx).asInstanceOf[Int]
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[ODocument]) map {_.asDataValue}
    AppliedArrayRemoveOperation(id, noOp, idx, oldValue)
  }

  private[domain] val DocumentClassName = "ArrayRemoveOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Idx = "idx"
    val OldValue = "oldValue"
  }
}
