package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions

import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument

object ArrayReplaceOperationMapper {

  import ArrayReplaceOperationFields._

  private[domain] implicit class ArrayReplaceOperationToODocument(val s: ArrayReplaceOperation) extends AnyVal {
    def asODocument: ODocument = arrayReplaceOperationToODocument(s)
  }

  private[domain] implicit def arrayReplaceOperationToODocument(obj: ArrayReplaceOperation): ODocument = {
    val ArrayReplaceOperation(path, noOp, index, value) = obj
    val doc = new ODocument(ArrayReplaceOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Idx, index)
    doc.field(Val, JValueMapper.jValueToJava(value))
    doc
  }

  private[domain] implicit class ODocumentToArrayReplaceOperation(val d: ODocument) extends AnyVal {
    def asArrayReplaceOperation: ArrayReplaceOperation = oDocumentToArrayReplaceOperation(d)
  }

  private[domain] implicit def oDocumentToArrayReplaceOperation(doc: ODocument): ArrayReplaceOperation = {
    if (doc.getClassName != ArrayReplaceOperationClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${ArrayReplaceOperationClassName}': ${doc.getClassName}")
    }
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Idx).asInstanceOf[Int]
    val value = JValueMapper.javaToJValue(doc.field(Val)) 
    ArrayReplaceOperation(path.asScala.toList, noOp, idx, value)
  }

  private[domain] val ArrayReplaceOperationClassName = "ArrayReplaceOperation"

  private[domain] object ArrayReplaceOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
  }
}