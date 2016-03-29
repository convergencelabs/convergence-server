package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import org.json4s.JsonAST.JArray
import com.convergencelabs.server.datastore.mapper.ODocumentMapper
import DataValueMapper.DataValueToODocument
import DataValueMapper.ODocumentToDataValue

object ArraySetOperationMapper extends ODocumentMapper {

  private[domain] implicit class ArraySetOperationToODocument(val s: ArraySetOperation) extends AnyVal {
    def asODocument: ODocument = arraySetOperationToODocument(s)
  }

  private[domain] implicit def arraySetOperationToODocument(obj: ArraySetOperation): ODocument = {
    val ArraySetOperation(id, noOp, value) = obj
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.NoOp, noOp)
    var docValue = value map(v => v.asODocument);
    doc.field(Fields.Val, docValue.asJava)
    doc
  }

  private[domain] implicit class ODocumentToArraySetOperation(val d: ODocument) extends AnyVal {
    def asArraySetOperation: ArraySetOperation = oDocumentToArraySetOperation(d)
  }

  private[domain] implicit def oDocumentToArraySetOperation(doc: ODocument): ArraySetOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val id = doc.field(Fields.Id).asInstanceOf[String]
    val noOp = doc.field(Fields.NoOp).asInstanceOf[Boolean]
    val value = doc.field(Fields.Val).asInstanceOf[JavaList[ODocument]].asScala.toList.map {v => v.asDataValue}
    ArraySetOperation(id, noOp, value)
  }

  private[domain] val DocumentClassName = "ArraySetOperation"

  private[domain] object Fields {
    val Id = "vid"
    val NoOp = "noOp"
    val Val = "val"
  }
}
