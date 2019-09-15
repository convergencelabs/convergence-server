package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.metadata.schema.OType

object StringRemoveOperationMapper extends ODocumentMapper {

  private[domain] implicit class StringRemoveOperationToODocument(val s: AppliedStringRemoveOperation) extends AnyVal {
    def asODocument: ODocument = stringRemoveOperationToODocument(s)
  }

  private[domain] implicit def stringRemoveOperationToODocument(obj: AppliedStringRemoveOperation): ODocument = {
    val AppliedStringRemoveOperation(id, noOp, index, length, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.Length, length)
    doc.field(Fields.OldValue, oldValue.getOrElse(null))   
    doc
  }

  private[domain] implicit class ODocumentToStringRemoveOperation(val d: ODocument) extends AnyVal {
    def asStringRemoveOperation: AppliedStringRemoveOperation = oDocumentToStringRemoveOperation(d)
  }

  private[domain] implicit def oDocumentToStringRemoveOperation(doc: ODocument): AppliedStringRemoveOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val index = doc.field(Fields.Idx).asInstanceOf[Int]
    val length = doc.field(Fields.Length).asInstanceOf[Int]
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[String])
    AppliedStringRemoveOperation(id, noOp, index, length, oldValue)
  }

  private[domain] val DocumentClassName = "StringRemoveOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Length = "length"
    val Idx = "idx"
    val OldValue = "oldVal"
  }
}
