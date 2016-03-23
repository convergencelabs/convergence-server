package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object ArrayReplaceOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArrayReplaceOperationToODocument(val s: ArrayReplaceOperation) extends AnyVal {
    def asODocument: ODocument = arrayReplaceOperationToODocument(s)
  }

  private[domain] implicit def arrayReplaceOperationToODocument(obj: ArrayReplaceOperation): ODocument = {
    val ArrayReplaceOperation(id, noOp, index, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.Val, DataValueMapper.dataValueToODocument(value))
    doc
  }

  private[domain] implicit class ODocumentToArrayReplaceOperation(val d: ODocument) extends AnyVal {
    def asArrayReplaceOperation: ArrayReplaceOperation = oDocumentToArrayReplaceOperation(d)
  }

  private[domain] implicit def oDocumentToArrayReplaceOperation(doc: ODocument): ArrayReplaceOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Fields.Idx).asInstanceOf[Int]
    val value = DataValueMapper.oDocumentToDataValue(doc.field(Fields.Val))
    ArrayReplaceOperation(id, noOp, idx, value)
  }

  private[domain] val DocumentClassName = "ArrayReplaceOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
  }
}
