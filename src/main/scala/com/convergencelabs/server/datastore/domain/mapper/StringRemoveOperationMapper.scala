package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions

import com.convergencelabs.server.domain.model.ot.ops.StringRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object StringRemoveOperationMapper {

  import StringRemoveOperationFields._

  private[domain] implicit class StringRemoveOperationToODocument(val s: StringRemoveOperation) extends AnyVal {
    def asODocument: ODocument = stringRemoveOperationToODocument(s)
  }

  private[domain] implicit def stringRemoveOperationToODocument(obj: StringRemoveOperation): ODocument = {
    val StringRemoveOperation(path, noOp, index, value) = obj
    val doc = new ODocument(StringRemoveOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Idx, index)
    doc.field(Val, value)
    doc
  }

  private[domain] implicit class ODocumentToStringRemoveOperation(val d: ODocument) extends AnyVal {
    def asStringRemoveOperation: StringRemoveOperation = oDocumentToStringRemoveOperation(d)
  }

  private[domain] implicit def oDocumentToStringRemoveOperation(doc: ODocument): StringRemoveOperation = {
    assert(doc.getClassName == StringRemoveOperationClassName)
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val index = doc.field(Idx).asInstanceOf[Int]
    val value = doc.field(Val).asInstanceOf[String]
    StringRemoveOperation(path.asScala.toList, noOp, index, value)
  }

  private[domain] val StringRemoveOperationClassName = "StringRemoveOperation"

  private[domain] object StringRemoveOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
  }
}