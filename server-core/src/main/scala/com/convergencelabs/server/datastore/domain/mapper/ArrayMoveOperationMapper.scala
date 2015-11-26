package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions

import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument

object ArrayMoveOperationMapper {

  import ArrayMoveOperationFields._

  private[domain] implicit class ArrayMoveOperationToODocument(val s: ArrayMoveOperation) extends AnyVal {
    def asODocument: ODocument = arrayMoveOperationToODocument(s)
  }

  private[domain] implicit def arrayMoveOperationToODocument(obj: ArrayMoveOperation): ODocument = {
    val ArrayMoveOperation(path, noOp, from, to) = obj
    val doc = new ODocument(ArrayMoveOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(From, from)
    doc.field(To, to)
    doc
  }

  private[domain] implicit class ODocumentToArrayMoveOperation(val d: ODocument) extends AnyVal {
    def asArrayMoveOperation: ArrayMoveOperation = oDocumentToArrayMoveOperation(d)
  }

  private[domain] implicit def oDocumentToArrayMoveOperation(doc: ODocument): ArrayMoveOperation = {
    assert(doc.getClassName == ArrayMoveOperationClassName)
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val from = doc.field(From).asInstanceOf[Int]
    val to = doc.field(To).asInstanceOf[Int]
    ArrayMoveOperation(path.asScala.toList, noOp, from, to)
  }

  private[domain] val ArrayMoveOperationClassName = "ArrayMoveOperation"

  private[domain] object ArrayMoveOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val From = "from"
    val To = "to"
  }
}