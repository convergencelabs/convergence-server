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

object ModelSnapshotMapper {

  import ModelSnapshotFields._

  private[domain] implicit class ModelSnapshotToODocument(val m: ModelSnapshot) extends AnyVal {
    def asODocument: ODocument = modelToODocument(m)
  }

  private[domain] implicit def modelToODocument(modelSnapshot: ModelSnapshot): ODocument = {
    val doc = new ODocument(ModelSnapshotClassName)
    doc.field(CollectionId, modelSnapshot.metaData.fqn.collectionId)
    doc.field(ModelId, modelSnapshot.metaData.fqn.modelId)
    doc.field(Version, modelSnapshot.metaData.version)
    doc.field(Timestamp, new java.util.Date(modelSnapshot.metaData.timestamp.toEpochMilli()))
    doc.field(Data, JValueMapper.jValueToJava(modelSnapshot.data))
    doc
  }

  private[domain] implicit class ODocumentToModelSnapshot(val d: ODocument) extends AnyVal {
    def asModelSnapshot: ModelSnapshot = oDocumentToModelSnapshot(d)
  }

  private[domain] implicit def oDocumentToModelSnapshot(doc: ODocument): ModelSnapshot = {
    if (doc.getClassName != ModelSnapshotClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${ModelSnapshotClassName}': ${doc.getClassName}")
    }

    // FIXME this assumes every thing is an object.
    val dataMap: java.util.Map[String, Any] = doc.field(Data)
    val data = JValueMapper.javaToJValue(dataMap)
    ModelSnapshot(oDocumentToModelSnapshotMetaData(doc), data)
  }

  private[domain] implicit class ODocumentToModelSnapshotMetaData(val d: ODocument) extends AnyVal {
    def asModelSnapshotMetaData: ModelSnapshotMetaData = oDocumentToModelSnapshotMetaData(d)
  }

  private[domain] implicit def oDocumentToModelSnapshotMetaData(doc: ODocument): ModelSnapshotMetaData = {
    val timestamp: java.util.Date = doc.field(Timestamp)
    ModelSnapshotMetaData(
      ModelFqn(
        doc.field(CollectionId),
        doc.field(ModelId)),
      doc.field(Version),
      Instant.ofEpochMilli(timestamp.getTime))
  }

  private[domain] val ModelSnapshotClassName = "ModelSnapshot"

  private[domain] object ModelSnapshotFields {
    val CollectionId = "collectionId"
    val ModelId = "modelId"
    val Version = "version"
    val Timestamp = "timestamp"
    val Data = "data"
  }
}