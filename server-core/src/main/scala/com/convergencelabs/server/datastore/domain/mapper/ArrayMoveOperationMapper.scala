package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object ArrayMoveOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArrayMoveOperationToODocument(val s: ArrayMoveOperation) extends AnyVal {
    def asODocument: ODocument = arrayMoveOperationToODocument(s)
  }

  private[domain] implicit def arrayMoveOperationToODocument(obj: ArrayMoveOperation): ODocument = {
    val ArrayMoveOperation(path, noOp, from, to) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Path, path.asJava)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.From, from)
    doc.field(Fields.To, to)
    doc
  }

  private[domain] implicit class ODocumentToArrayMoveOperation(val d: ODocument) extends AnyVal {
    def asArrayMoveOperation: ArrayMoveOperation = oDocumentToArrayMoveOperation(d)
  }

  private[domain] implicit def oDocumentToArrayMoveOperation(doc: ODocument): ArrayMoveOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val path = doc.field(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val from = doc.field(Fields.From).asInstanceOf[Int]
    val to = doc.field(Fields.To).asInstanceOf[Int]
    ArrayMoveOperation(path.asScala.toList, noOp, from, to)
  }

  private[domain] val DocumentClassName = "ArrayMoveOperation"

  private[domain] object Fields {
    val Path = "path"
    val NoOp = "noOp"
    val From = "fromIdx"
    val To = "toIdx"
  }
}
