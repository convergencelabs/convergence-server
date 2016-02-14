package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions

import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object BooleanSetOperationMapper extends ODocumentMapper {

  private[domain] implicit class BooleanSetOperationToODocument(val s: BooleanSetOperation) extends AnyVal {
    def asODocument: ODocument = numberSetOperationToODocument(s)
  }

  private[domain] implicit def numberSetOperationToODocument(op: BooleanSetOperation): ODocument = {
    val BooleanSetOperation(path, noOp, value) = op
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Path, path.asJava)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Val, value)
    doc
  }

  private[domain] implicit class ODocumentToBooleanSetOperation(val d: ODocument) extends AnyVal {
    def asBooleanSetOperation: BooleanSetOperation = oDocumentToBooleanSetOperation(d)
  }

  private[domain] implicit def oDocumentToBooleanSetOperation(doc: ODocument): BooleanSetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val path = doc.field(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[Boolean]
    BooleanSetOperation(path.asScala.toList, noOp, value)
  }

  private[domain] val DocumentClassName = "BooleanSetOperation"

  private[domain] object Fields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
  }
}
