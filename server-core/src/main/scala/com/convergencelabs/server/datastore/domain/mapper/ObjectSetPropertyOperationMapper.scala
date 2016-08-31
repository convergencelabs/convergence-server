package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue
import com.orientechnologies.orient.core.metadata.schema.OType

object ObjectSetPropertyOperationMapper extends ODocumentMapper {

  private[domain] implicit class ObjectSetPropertyOperationToODocument(val s: AppliedObjectSetPropertyOperation) extends AnyVal {
    def asODocument: ODocument = objectSetPropertyOperationToODocument(s)
  }

  private[domain] implicit def objectSetPropertyOperationToODocument(obj: AppliedObjectSetPropertyOperation): ODocument = {
    val AppliedObjectSetPropertyOperation(id, noOp, prop, value, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Prop, prop)
    doc.field(Fields.Val, value.asODocument, OType.EMBEDDED)
    val oldValDoc = (oldValue map {_.asODocument})
    doc.field(Fields.OldValue, oldValDoc.getOrElse(null))
    doc
  }

  private[domain] implicit class ODocumentToObjectSetPropertyOperation(val d: ODocument) extends AnyVal {
    def asObjectSetPropertyOperation: AppliedObjectSetPropertyOperation = oDocumentToObjectSetPropertyOperation(d)
  }

  private[domain] implicit def oDocumentToObjectSetPropertyOperation(doc: ODocument): AppliedObjectSetPropertyOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val prop = doc.field(Fields.Prop).asInstanceOf[String]
    val value = doc.field(Fields.Val).asInstanceOf[ODocument].asDataValue
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[ODocument]) map {_.asDataValue}
    AppliedObjectSetPropertyOperation(id, noOp, prop, value, oldValue)
  }

  private[domain] val DocumentClassName = "ObjectSetPropertyOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Prop = "prop"
    val Val = "val"
    val OldValue = "oldVal"
  }
}
