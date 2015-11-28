package com.convergencelabs.server.datastore.domain.mapper

import java.time.Instant
import scala.annotation.elidable
import scala.annotation.elidable.ASSERTION
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelOperation
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import java.util.Date

object ModelOperationMapper {

  import ModelOperationFields._

  private[domain] implicit class ModelOperationToODocument(val s: ModelOperation) extends AnyVal {
    def asODocument: ODocument = modelOperationToODocument(s)
  }

  private[domain] implicit def modelOperationToODocument(opEvent: ModelOperation): ODocument = {
    val doc = new ODocument(ModelOperationClassName)
    doc.field(CollectionId, opEvent.modelFqn.collectionId)
    doc.field(ModelId, opEvent.modelFqn.modelId)
    doc.field(Version, opEvent.version)
    doc.field(Timestamp, Date.from(opEvent.timestamp))
    doc.field(Uid, opEvent.uid)
    doc.field(Sid, opEvent.sid)
    doc.field(Operation, OrientDBOperationMapper.operationToODocument(opEvent.op))
  }

  private[domain] implicit class ODocumentToModelOperation(val d: ODocument) extends AnyVal {
    def asModelOperation: ModelOperation = oDocumentToModelOperation(d)
  }

  private[domain] implicit def oDocumentToModelOperation(doc: ODocument): ModelOperation = {
    if (doc.getClassName != ModelOperationClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${ModelOperationClassName}': ${doc.getClassName}")
    }

    val docDate: java.util.Date = doc.field(Timestamp, OType.DATETIME)
    val timestamp = Instant.ofEpochMilli(docDate.getTime)
    val opDoc: ODocument = doc.field(Operation, OType.EMBEDDED)
    val op = OrientDBOperationMapper.oDocumentToDiscreteOperation(opDoc)
    ModelOperation(
      ModelFqn(doc.field(CollectionId), doc.field(ModelId)),
      doc.field(Version),
      timestamp,
      doc.field(Uid),
      doc.field(Sid),
      op)
  }

  private[domain] val ModelOperationClassName = "ModelOperation"

  private[domain] object ModelOperationFields {
    val Version = "version"
    val ModelId = "modelId"
    val CollectionId = "collectionId"
    val Timestamp = "timestamp"
    val Uid = "uid"
    val Sid = "sid"
    val Operation = "op"
    val Data = "data"
  }
}