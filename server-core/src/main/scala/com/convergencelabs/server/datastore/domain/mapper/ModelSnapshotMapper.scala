package com.convergencelabs.server.datastore.domain.mapper

import java.time.Instant
import java.util.Date
import java.util.{ Map => JavaMap }
import scala.language.implicitConversions
import com.convergencelabs.server.domain.model.ModelSnapshot
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelSnapshotMetaData
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object ModelSnapshotMapper extends ODocumentMapper {

  private[domain] implicit class ModelSnapshotToODocument(val m: ModelSnapshot) extends AnyVal {
    def asODocument: ODocument = modelToODocument(m)
  }

  private[domain] implicit def modelToODocument(modelSnapshot: ModelSnapshot): ODocument = {
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.CollectionId, modelSnapshot.metaData.fqn.collectionId)
    doc.field(Fields.ModelId, modelSnapshot.metaData.fqn.modelId)
    doc.field(Fields.Version, modelSnapshot.metaData.version)
    doc.field(Fields.Timestamp, new java.util.Date(modelSnapshot.metaData.timestamp.toEpochMilli()))
    doc.field(Fields.Data, JValueMapper.jValueToJava(modelSnapshot.data))
    doc
  }

  private[domain] implicit class ODocumentToModelSnapshot(val d: ODocument) extends AnyVal {
    def asModelSnapshot: ModelSnapshot = oDocumentToModelSnapshot(d)
  }

  private[domain] implicit def oDocumentToModelSnapshot(doc: ODocument): ModelSnapshot = {
    validateDocumentClass(doc, DocumentClassName)

    // FIXME this assumes every thing is an object.
    val dataMap: java.util.Map[String, Any] = doc.field(Fields.Data)
    val data = JValueMapper.javaToJValue(dataMap)
    ModelSnapshot(oDocumentToModelSnapshotMetaData(doc), data)
  }

  private[domain] implicit class ODocumentToModelSnapshotMetaData(val d: ODocument) extends AnyVal {
    def asModelSnapshotMetaData: ModelSnapshotMetaData = oDocumentToModelSnapshotMetaData(d)
  }

  private[domain] implicit def oDocumentToModelSnapshotMetaData(doc: ODocument): ModelSnapshotMetaData = {
    val timestamp: java.util.Date = doc.field(Fields.Timestamp)
    ModelSnapshotMetaData(
      ModelFqn(
        doc.field(Fields.CollectionId),
        doc.field(Fields.ModelId)),
      doc.field(Fields.Version),
      Instant.ofEpochMilli(timestamp.getTime))
  }

  private[domain] val DocumentClassName = "ModelSnapshot"

  private[domain] object Fields {
    val CollectionId = "collectionId"
    val ModelId = "modelId"
    val Version = "version"
    val Timestamp = "timestamp"
    val Data = "data"
  }
}
