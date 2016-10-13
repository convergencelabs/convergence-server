package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue

object ArraySetOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArraySetOperationToODocument(val s: AppliedArraySetOperation) extends AnyVal {
    def asODocument: ODocument = arraySetOperationToODocument(s)
  }

  private[domain] implicit def arraySetOperationToODocument(obj: AppliedArraySetOperation): ODocument = {
    val AppliedArraySetOperation(id, noOp, value, oldValue) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    var docValue = value map(_.asODocument);
    doc.field(Fields.Val, docValue.asJava)
    var oldValueDoc = oldValue map {_ map {_.asODocument}} map {_.asJava};
    doc.field(Fields.OldValue, oldValueDoc.getOrElse(null))
    doc
  }

  private[domain] implicit class ODocumentToArraySetOperation(val d: ODocument) extends AnyVal {
    def asArraySetOperation: AppliedArraySetOperation = oDocumentToArraySetOperation(d)
  }

  private[domain] implicit def oDocumentToArraySetOperation(doc: ODocument): AppliedArraySetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[JavaList[ODocument]].asScala.toList.map {v => v.asDataValue}
    val oldValue = Option(doc.field(Fields.OldValue).asInstanceOf[JavaList[ODocument]]) map {_.asScala.toList.map {_.asDataValue}}
    AppliedArraySetOperation(id, noOp, value, oldValue)
  }

  private[domain] val DocumentClassName = "ArraySetOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
    val OldValue = "oldVal"
  }
}
