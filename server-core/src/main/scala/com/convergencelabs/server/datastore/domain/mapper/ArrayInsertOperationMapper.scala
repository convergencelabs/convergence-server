package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.util.JValueMapper

object ArrayInsertOperationMapper {

  import ArrayInsertOperationFields._

  private[domain] implicit class ArrayInsertOperationToODocument(val s: ArrayInsertOperation) extends AnyVal {
    def asODocument: ODocument = arrayInsertOperationToODocument(s)
  }

  private[domain] implicit def arrayInsertOperationToODocument(obj: ArrayInsertOperation): ODocument = {
    val ArrayInsertOperation(path, noOp, index, value) = obj
    val doc = new ODocument(ArrayInsertOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Idx, index)
    doc.field(Val, JValueMapper.jValueToJava(value))
    doc
  }

  private[domain] implicit class ODocumentToArrayInsertOperation(val d: ODocument) extends AnyVal {
    def asArrayInsertOperation: ArrayInsertOperation = oDocumentToArrayInsertOperation(d)
  }

  private[domain] implicit def oDocumentToArrayInsertOperation(doc: ODocument): ArrayInsertOperation = {
    if (doc.getClassName != ArrayInsertOperationClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${ArrayInsertOperationClassName}': ${doc.getClassName}")
    }
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Idx).asInstanceOf[Int]
    val value = JValueMapper.javaToJValue(doc.field(Val))
    ArrayInsertOperation(path.asScala.toList, noOp, idx, value)
  }

  private[domain] val ArrayInsertOperationClassName = "ArrayInsertOperation"

  private[domain] object ArrayInsertOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
  }
}