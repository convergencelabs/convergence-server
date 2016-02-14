package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.util.JValueMapper
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object ArrayRemoveOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArrayRemoveOperationToODocument(val s: ArrayRemoveOperation) extends AnyVal {
    def asODocument: ODocument = arrayRemoveOperationToODocument(s)
  }

  private[domain] implicit def arrayRemoveOperationToODocument(obj: ArrayRemoveOperation): ODocument = {
    val ArrayRemoveOperation(path, noOp, index) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Path, path.asJava)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc
  }

  private[domain] implicit class ODocumentToArrayRemoveOperation(val d: ODocument) extends AnyVal {
    def asArrayRemoveOperation: ArrayRemoveOperation = oDocumentToArrayRemoveOperation(d)
  }

  private[domain] implicit def oDocumentToArrayRemoveOperation(doc: ODocument): ArrayRemoveOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val path = doc.field(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Fields.Idx).asInstanceOf[Int]
    ArrayRemoveOperation(path.asScala.toList, noOp, idx)
  }

  private[domain] val DocumentClassName = "ArrayRemoveOperation"

  private[domain] object Fields {
    val Path = "path"
    val NoOp = "noOp"
    val Idx = "idx"
  }
}
