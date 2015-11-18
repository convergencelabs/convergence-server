package com.convergencelabs.server.datastore.mapper

import com.convergencelabs.server.domain.model.SnapshotConfig
import com.orientechnologies.orient.core.record.impl.ODocument
import java.time.Duration
import scala.language.implicitConversions

object SnapshotConfigMapper {
  
  implicit class SnapshotConfigToODocument(val snapshotConfig: SnapshotConfig) {
    def asODocument: ODocument = snapshotConfigToODocument(snapshotConfig)
  }
  
  implicit def snapshotConfigToODocument(snapshotConfig: SnapshotConfig): ODocument = {
    val doc = new ODocument("SnapshotConfig")
    doc.field(Fields.Enabled, snapshotConfig.snapshotsEnabled)
    doc.field(Fields.TriggerByVersion, snapshotConfig.triggerByVersion)
    doc.field(Fields.LimitedByVersion, snapshotConfig.limitedByVersion)
    doc.field(Fields.MinVersionInterval, snapshotConfig.minimumVersionInterval)
    doc.field(Fields.MaxVersionInterval, snapshotConfig.maximumVersionInterval)
    doc.field(Fields.TriggerByTime, snapshotConfig.triggerByTime)
    doc.field(Fields.LimitedByTime, snapshotConfig.limitedByTime)
    doc.field(Fields.MinTimeInterval, snapshotConfig.minimumTimeInterval.toMillis)
    doc.field(Fields.MaxTimeInterval, snapshotConfig.maximumTimeInterval.toMillis)
  }
  
  implicit class ODocumentToSnapshotConfig(val doc: ODocument) {
     def asSnapshotConfig: SnapshotConfig = oDocumentToSnapshotConfig(doc)
  }
  
  implicit def oDocumentToSnapshotConfig(doc: ODocument): SnapshotConfig = {
    val minTimeIntervalMillis: Long = doc.field(Fields.MinTimeInterval)
    val maxTimeIntervalMillis: Long = doc.field(Fields.MaxTimeInterval)

    SnapshotConfig(
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
  
  private[this] object Fields {
    val MinTimeInterval = "minTimeInterval"
    val MaxTimeInterval = "maxTimeInterval"
    val Enabled = "enabled"
    val TriggerByVersion = "triggerByVersion"
    val LimitedByVersion = "limitedByVersion"
    val MinVersionInterval = "minVersionInterval"
    val MaxVersionInterval = "maxVersionInterval"
    val TriggerByTime = "triggerByTime"
    val LimitedByTime = "limitedByTime"
  }
}