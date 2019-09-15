package com.convergencelabs.server.datastore.domain.mapper

import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue

object ObjectRemovePropertyOperationMapper extends ODocumentMapper {

  private[domain] implicit class ObjectRemovePropertyOperationToODocument(val s: AppliedObjectRemovePropertyOperation) extends AnyVal {
    def asODocument: ODocument = objectRemovePropertyOperationToODocument(s)
  }

  private[domain] implicit def objectRemovePropertyOperationToODocument(obj: AppliedObjectRemovePropertyOperation): ODocument = {
    val AppliedObjectRemovePropertyOperation(id, noOp, prop, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Prop, prop)
    val oldValDoc = (oldValue map {_.asODocument})
    doc.field(Fields.OldValue, oldValDoc.getOrElse(null))
    doc
  }

  private[domain] implicit class ODocumentToObjectRemovePropertyOperation(val d: ODocument) extends AnyVal {
    def asObjectRemovePropertyOperation: AppliedObjectRemovePropertyOperation = oDocumentToObjectRemovePropertyOperation(d)
  }

  private[domain] implicit def oDocumentToObjectRemovePropertyOperation(doc: ODocument): AppliedObjectRemovePropertyOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val prop = doc.field(Fields.Prop).asInstanceOf[String]
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[ODocument]) map {_.asDataValue}
    AppliedObjectRemovePropertyOperation(id, noOp, prop, oldValue)
  }

  private[domain] val DocumentClassName = "ObjectRemovePropertyOperation"

  private[domain] object Fields {
    val Id = "elementId"
    val NoOp = "noOp"
    val Prop = "prop"
    val Val = "val"
    val OldValue = "oldVal"
  }
}
