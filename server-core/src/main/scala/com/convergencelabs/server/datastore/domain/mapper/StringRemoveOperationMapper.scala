package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object StringRemoveOperationMapper extends ODocumentMapper {

  private[domain] implicit class StringRemoveOperationToODocument(val s: StringRemoveOperation) extends AnyVal {
    def asODocument: ODocument = stringRemoveOperationToODocument(s)
  }

  private[domain] implicit def stringRemoveOperationToODocument(obj: StringRemoveOperation): ODocument = {
    val StringRemoveOperation(path, noOp, index, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Path, path.asJava)
    doc.field(Fields.NoOp, noOp)
    doc.field(Fields.Idx, index)
    doc.field(Fields.Val, value)
    doc
  }

  private[domain] implicit class ODocumentToStringRemoveOperation(val d: ODocument) extends AnyVal {
    def asStringRemoveOperation: StringRemoveOperation = oDocumentToStringRemoveOperation(d)
  }

  private[domain] implicit def oDocumentToStringRemoveOperation(doc: ODocument): StringRemoveOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val path = doc.field(Fields.Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val index = doc.field(Fields.Idx).asInstanceOf[Int]
    val value = doc.field(Fields.Val).asInstanceOf[String]
    StringRemoveOperation(path.asScala.toList, noOp, index, value)
  }

  private[domain] val DocumentClassName = "StringRemoveOperation"

  private[domain] object Fields {
    val Path = "path"
    val NoOp = "noOp"
    val Val = "val"
    val Idx = "idx"
  }
}
