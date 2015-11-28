package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.util.JValueMapper

object ArrayRemoveOperationMapper {

  import ArrayRemoveOperationFields._

  private[domain] implicit class ArrayRemoveOperationToODocument(val s: ArrayRemoveOperation) extends AnyVal {
    def asODocument: ODocument = arrayRemoveOperationToODocument(s)
  }

  private[domain] implicit def arrayRemoveOperationToODocument(obj: ArrayRemoveOperation): ODocument = {
    val ArrayRemoveOperation(path, noOp, index) = obj
    val doc = new ODocument(ArrayRemoveOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Idx, index)
    doc
  }

  private[domain] implicit class ODocumentToArrayRemoveOperation(val d: ODocument) extends AnyVal {
    def asArrayRemoveOperation: ArrayRemoveOperation = oDocumentToArrayRemoveOperation(d)
  }

  private[domain] implicit def oDocumentToArrayRemoveOperation(doc: ODocument): ArrayRemoveOperation = {
    if (doc.getClassName != ArrayRemoveOperationClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${ArrayRemoveOperationClassName}': ${doc.getClassName}")
    }
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val idx = doc.field(Idx).asInstanceOf[Int]
    ArrayRemoveOperation(path.asScala.toList, noOp, idx)
  }

  private[domain] val ArrayRemoveOperationClassName = "ArrayRemoveOperation"

  private[domain] object ArrayRemoveOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Idx = "idx"
  }
}