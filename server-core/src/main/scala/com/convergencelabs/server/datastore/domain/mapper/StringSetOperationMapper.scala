package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object StringSetOperationMapper extends ODocumentMapper {

  private[domain] implicit class StringSetOperationToODocument(val s: StringSetOperation) extends AnyVal {
    def asODocument: ODocument = stringSetOperationToODocument(s)
  }

  private[domain] implicit def stringSetOperationToODocument(obj: StringSetOperation): ODocument = {
    val StringSetOperation(id, noOp, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Val, value)
    doc
  }

  private[domain] implicit class ODocumentToStringSetOperation(val d: ODocument) extends AnyVal {
    def asStringSetOperation: StringSetOperation = oDocumentToStringSetOperation(d)
  }

  private[domain] implicit def oDocumentToStringSetOperation(doc: ODocument): StringSetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[String]
    StringSetOperation(id, noOp, value)
  }

  private[domain] val DocumentClassName = "StringSetOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
  }
}
