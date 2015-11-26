package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions

import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

object StringSetOperationMapper {

  import StringSetOperationFields._

  private[domain] implicit class StringSetOperationToODocument(val s: StringSetOperation) extends AnyVal {
    def asODocument: ODocument = stringSetOperationToODocument(s)
  }

  private[domain] implicit def stringSetOperationToODocument(obj: StringSetOperation): ODocument = {
    val StringSetOperation(path, noOp, value) = obj
    val doc = new ODocument(StringSetOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Val, value)
    doc
  }

  private[domain] implicit class ODocumentToStringSetOperation(val d: ODocument) extends AnyVal {
    def asStringSetOperation: StringSetOperation = oDocumentToStringSetOperation(d)
  }

  private[domain] implicit def oDocumentToStringSetOperation(doc: ODocument): StringSetOperation = {
    assert(doc.getClassName == StringSetOperationClassName)
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val value = doc.field(Val).asInstanceOf[String]
    StringSetOperation(path.asScala.toList, noOp, value)
  }

  private[domain] val StringSetOperationClassName = "StringSetOperation"

  private[domain] object StringSetOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
  }
}