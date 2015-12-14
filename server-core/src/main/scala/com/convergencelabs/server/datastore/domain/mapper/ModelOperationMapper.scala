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
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object ModelOperationMapper extends ODocumentMapper {

  private[domain] implicit class ModelOperationToODocument(val s: ModelOperation) extends AnyVal {
    def asODocument: ODocument = modelOperationToODocument(s)
  }

  private[domain] implicit def modelOperationToODocument(opEvent: ModelOperation): ODocument = {
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.CollectionId, opEvent.modelFqn.collectionId)
    doc.field(Fields.ModelId, opEvent.modelFqn.modelId)
    doc.field(Fields.Version, opEvent.version)
    doc.field(Fields.Timestamp, Date.from(opEvent.timestamp))
    doc.field(Fields.Uid, opEvent.uid)
    doc.field(Fields.Sid, opEvent.sid)
    doc.field(Fields.Operation, OrientDBOperationMapper.operationToODocument(opEvent.op))
    doc
  }

  private[domain] implicit class ODocumentToModelOperation(val d: ODocument) extends AnyVal {
    def asModelOperation: ModelOperation = oDocumentToModelOperation(d)
  }

  private[domain] implicit def oDocumentToModelOperation(doc: ODocument): ModelOperation = {
    validateDocumentClass(doc, DocumentClassName)

    val docDate: java.util.Date = doc.field(Fields.Timestamp, OType.DATETIME)
    val timestamp = Instant.ofEpochMilli(docDate.getTime)
    val opDoc: ODocument = doc.field(Fields.Operation, OType.EMBEDDED)
    val op = OrientDBOperationMapper.oDocumentToOperation(opDoc)
    ModelOperation(
      ModelFqn(doc.field(Fields.CollectionId), doc.field(Fields.ModelId)),
      doc.field(Fields.Version),
      timestamp,
      doc.field(Fields.Uid),
      doc.field(Fields.Sid),
      op)
  }

  private[domain] val DocumentClassName = "ModelOperation"

  private[domain] object Fields {
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
