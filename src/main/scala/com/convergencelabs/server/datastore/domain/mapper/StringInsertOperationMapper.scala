package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions

import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object StringInsertOperationMapper {

  import StringInsertOperationFields._

  private[domain] implicit class StringInsertOperationToODocument(val s: StringInsertOperation) extends AnyVal {
    def asODocument: ODocument = stringInsertOperationToODocument(s)
  }

  private[domain] implicit def stringInsertOperationToODocument(obj: StringInsertOperation): ODocument = {
    val StringInsertOperation(path, noOp, index, value) = obj
    val doc = new ODocument(StringInsertOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Idx, index)
    doc.field(Val, value)
    doc
  }

  private[domain] implicit class ODocumentToStringInsertOperation(val d: ODocument) extends AnyVal {
    def asStringInsertOperation: StringInsertOperation = oDocumentToStringInsertOperation(d)
  }

  private[domain] implicit def oDocumentToStringInsertOperation(doc: ODocument): StringInsertOperation = {
    assert(doc.getClassName == StringInsertOperationClassName)
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val index = doc.field(Idx).asInstanceOf[Int]
    val value = doc.field(Val).asInstanceOf[String]
    StringInsertOperation(path.asScala.toList, noOp, index, value)
  }

  private[domain] val StringInsertOperationClassName = "StringInsertOperation"

  private[domain] object StringInsertOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
  }
}