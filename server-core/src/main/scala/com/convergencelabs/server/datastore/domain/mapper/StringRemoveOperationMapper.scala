package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object StringRemoveOperationMapper extends ODocumentMapper {

  private[domain] implicit class StringRemoveOperationToODocument(val s: StringRemoveOperation) extends AnyVal {
    def asODocument: ODocument = stringRemoveOperationToODocument(s)
  }

  private[domain] implicit def stringRemoveOperationToODocument(obj: StringRemoveOperation): ODocument = {
    val StringRemoveOperation(id, noOp, index, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.Val, value)
    doc
  }

  private[domain] implicit class ODocumentToStringRemoveOperation(val d: ODocument) extends AnyVal {
    def asStringRemoveOperation: StringRemoveOperation = oDocumentToStringRemoveOperation(d)
  }

  private[domain] implicit def oDocumentToStringRemoveOperation(doc: ODocument): StringRemoveOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val index = doc.field(Fields.Idx).asInstanceOf[Int]
    val value = doc.field(Fields.Val).asInstanceOf[String]
    StringRemoveOperation(id, noOp, index, value)
  }

  private[domain] val DocumentClassName = "StringRemoveOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
  }
}
