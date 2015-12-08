package com.convergencelabs.server.datastore.domain.mapper

import com.orientechnologies.orient.core.record.impl.ODocument
import java.time.Duration
import scala.language.implicitConversions
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.datastore.mapper.ODocumentMapper

object ModelSnapshotConfigMapper extends ODocumentMapper {

  private[domain] implicit class ModelSnapshotConfigToODocument(val snapshotConfig: ModelSnapshotConfig) extends AnyVal {
    def asODocument: ODocument = snapshotConfigToODocument(snapshotConfig)
  }

  private[domain] implicit def snapshotConfigToODocument(snapshotConfig: ModelSnapshotConfig): ODocument = {
    val doc = new ODocument(DocumentClassName)
    doc.field(Fields.Enabled, snapshotConfig.snapshotsEnabled)
    doc.field(Fields.TriggerByVersion, snapshotConfig.triggerByVersion)
    doc.field(Fields.LimitedByVersion, snapshotConfig.limitedByVersion)
    doc.field(Fields.MinVersionInterval, snapshotConfig.minimumVersionInterval)
    doc.field(Fields.MaxVersionInterval, snapshotConfig.maximumVersionInterval)
    doc.field(Fields.TriggerByTime, snapshotConfig.triggerByTime)
    doc.field(Fields.LimitedByTime, snapshotConfig.limitedByTime)
    doc.field(Fields.MinTimeInterval, snapshotConfig.minimumTimeInterval.toMillis)
    doc.field(Fields.MaxTimeInterval, snapshotConfig.maximumTimeInterval.toMillis)
    doc
  }

  private[domain] implicit class ODocumentToModelSnapshotConfig(val doc: ODocument) extends AnyVal {
    def asModelSnapshotConfig: ModelSnapshotConfig = oDocumentToModelSnapshotConfig(doc)
  }

  private[domain] implicit def oDocumentToModelSnapshotConfig(doc: ODocument): ModelSnapshotConfig = {
    validateDocumentClass(doc, DocumentClassName)

    val minTimeIntervalMillis: Long = doc.field(Fields.MinTimeInterval)
    val maxTimeIntervalMillis: Long = doc.field(Fields.MaxTimeInterval)

    ModelSnapshotConfig(
      doc.field(Fields.Enabled).asInstanceOf[Boolean],
      doc.field(Fields.TriggerByVersion).asInstanceOf[Boolean],
      doc.field(Fields.LimitedByVersion).asInstanceOf[Boolean],
      doc.field(Fields.MinVersionInterval).asInstanceOf[Long],
      doc.field(Fields.MaxVersionInterval).asInstanceOf[Long],
      doc.field(Fields.TriggerByTime).asInstanceOf[Boolean],
      doc.field(Fields.LimitedByTime).asInstanceOf[Boolean],
      Duration.ofMillis(minTimeIntervalMillis),
      Duration.ofMillis(maxTimeIntervalMillis))
  }

  val DocumentClassName = "ModelSnapshotConfig"

  object Fields {
    val Enabled = "enabled"
    val MinTimeInterval = "minTimeInterval"
    val MaxTimeInterval = "maxTimeInterval"
    val TriggerByVersion = "triggerByVersion"
    val LimitedByVersion = "limitedByVersion"
    val MinVersionInterval = "minVersionInterval"
    val MaxVersionInterval = "maxVersionInterval"
    val TriggerByTime = "triggerByTime"
    val LimitedByTime = "limitedByTime"
  }
}
