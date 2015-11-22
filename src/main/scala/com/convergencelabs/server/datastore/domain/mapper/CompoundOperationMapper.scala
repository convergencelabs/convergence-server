package com.convergencelabs.server.datastore.domain.mapper

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ot.ops.CompoundOperation
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.metadata.schema.OType
import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation

object CompoundOperationMapper {

  import CompoundOperationFields._

  private[domain] implicit class CompoundOperationToODocument(val s: CompoundOperation) extends AnyVal {
    def asODocument: ODocument = arrayReplaceOperationToODocument(s)
  }

  private[domain] implicit def arrayReplaceOperationToODocument(obj: CompoundOperation): ODocument = {
    val CompoundOperation(ops) = obj
    val doc = new ODocument(CompoundOperationClassName)
    val opDocs = ops.map { OrientDBOperationMapper.operationToODocument(_) }
    doc.field(Ops, opDocs.asJava, OType.EMBEDDEDLIST)
    doc
  }

  private[domain] implicit class ODocumentToCompoundOperation(val d: ODocument) extends AnyVal {
    def asCompoundOperation: CompoundOperation = oDocumentToCompoundOperation(d)
  }

  private[domain] implicit def oDocumentToCompoundOperation(doc: ODocument): CompoundOperation = {
    if (doc.getClassName != CompoundOperationClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${CompoundOperationClassName}': ${doc.getClassName}")
    }
    
    val opDocs: JavaList[ODocument] = doc.field(Ops, OType.EMBEDDEDLIST)
    val ops = opDocs.asScala.toList.map {OrientDBOperationMapper.oDocumentToDiscreteOperation(_)}
    CompoundOperation(ops)
  }

  private[domain] val CompoundOperationClassName = "CompoundOperation"

  private[domain] object CompoundOperationFields {
    val Ops = "ops"
  }
}