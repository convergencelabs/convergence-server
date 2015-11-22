package com.convergencelabs.server.datastore.domain.mapper

import com.orientechnologies.orient.core.record.impl.ODocument
import scala.language.implicitConversions
import com.convergencelabs.server.datastore.domain.OrientDBOperationMapper
import com.convergencelabs.server.domain.model.ModelFqn
import java.time.Instant
import com.orientechnologies.orient.core.metadata.schema.OType
import com.convergencelabs.server.domain.model.ModelOperation

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
    doc.field(Timestamp, opEvent.timestamp)
    doc.field(Uid, opEvent.uid)
    doc.field(Sid, opEvent.sid)
    doc.field(Operation, OrientDBOperationMapper.operationToMap(opEvent.op))
  }

  private[domain] implicit class ODocumentToModelOperation(val d: ODocument) extends AnyVal {
    def asModelOperation: ModelOperation = oDocumentToModelOperation(d)
  }

  private[domain] implicit def oDocumentToModelOperation(doc: ODocument): ModelOperation = {
    val docDate: java.util.Date = doc.field(Timestamp, OType.DATETIME)
    assert(doc.getClassName == ModelOperationClassName)
    val timestamp = Instant.ofEpochMilli(docDate.getTime)
    val opMap: java.util.Map[String, Object] = doc.field(Operation, OType.EMBEDDEDMAP)
    val op = OrientDBOperationMapper.mapToOperation(opMap)
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