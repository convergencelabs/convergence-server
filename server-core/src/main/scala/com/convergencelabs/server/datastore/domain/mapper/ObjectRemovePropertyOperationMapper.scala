package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import org.json4s.JsonAST.JObject

object ObjectRemovePropertyOperationMapper {

  import ObjectRemovePropertyOperationFields._

  private[domain] implicit class ObjectRemovePropertyOperationToODocument(val s: ObjectRemovePropertyOperation) extends AnyVal {
    def asODocument: ODocument = objectRemovePropertyOperationToODocument(s)
  }

  private[domain] implicit def objectRemovePropertyOperationToODocument(obj: ObjectRemovePropertyOperation): ODocument = {
    val ObjectRemovePropertyOperation(path, noOp, prop) = obj
    val doc = new ODocument(ObjectRemovePropertyOperationClassName)
    doc.field(Path, path.asJava)
    doc.field(NoOp, noOp)
    doc.field(Prop, prop)
    doc
  }

  private[domain] implicit class ODocumentToObjectRemovePropertyOperation(val d: ODocument) extends AnyVal {
    def asObjectRemovePropertyOperation: ObjectRemovePropertyOperation = oDocumentToObjectRemovePropertyOperation(d)
  }

  private[domain] implicit def oDocumentToObjectRemovePropertyOperation(doc: ODocument): ObjectRemovePropertyOperation = {
    if (doc.getClassName != ObjectRemovePropertyOperationClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${ObjectRemovePropertyOperationClassName}': ${doc.getClassName}")
    }
    val path = doc.field(Path).asInstanceOf[JavaList[_]]
    val noOp = doc.field(NoOp).asInstanceOf[Boolean]
    val prop = doc.field(Prop).asInstanceOf[String]
    ObjectRemovePropertyOperation(path.asScala.toList, noOp, prop)
  }

  private[domain] val ObjectRemovePropertyOperationClassName = "ObjectRemovePropertyOperation"

  private[domain] object ObjectRemovePropertyOperationFields {
    val Path = "path"
    val NoOp = "noOp"
    val Prop = "prop"
    val Val = "val"
  }
}