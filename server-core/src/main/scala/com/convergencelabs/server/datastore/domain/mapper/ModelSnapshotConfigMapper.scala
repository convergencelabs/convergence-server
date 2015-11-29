package com.convergencelabs.server.datastore.domain.mapper

import com.orientechnologies.orient.core.record.impl.ODocument
import java.time.Duration
import scala.language.implicitConversions
import com.convergencelabs.server.domain.ModelSnapshotConfig

object ModelSnapshotConfigMapper {

  import ModelSnapshotConfigFields._

  private[domain] implicit class ModelSnapshotConfigToODocument(val snapshotConfig: ModelSnapshotConfig) extends AnyVal {
    def asODocument: ODocument = snapshotConfigToODocument(snapshotConfig)
  }

  private[domain] implicit def snapshotConfigToODocument(snapshotConfig: ModelSnapshotConfig): ODocument = {
    val doc = new ODocument(ModelSnapshotConfigClassName)
    doc.field(Enabled, snapshotConfig.snapshotsEnabled)
    doc.field(TriggerByVersion, snapshotConfig.triggerByVersion)
    doc.field(LimitedByVersion, snapshotConfig.limitedByVersion)
    doc.field(MinVersionInterval, snapshotConfig.minimumVersionInterval)
    doc.field(MaxVersionInterval, snapshotConfig.maximumVersionInterval)
    doc.field(TriggerByTime, snapshotConfig.triggerByTime)
    doc.field(LimitedByTime, snapshotConfig.limitedByTime)
    doc.field(MinTimeInterval, snapshotConfig.minimumTimeInterval.toMillis)
    doc.field(MaxTimeInterval, snapshotConfig.maximumTimeInterval.toMillis)
    doc
  }

  private[domain] implicit class ODocumentToModelSnapshotConfig(val doc: ODocument) extends AnyVal {
    def asModelSnapshotConfig: ModelSnapshotConfig = oDocumentToModelSnapshotConfig(doc)
  }

  private[domain] implicit def oDocumentToModelSnapshotConfig(doc: ODocument): ModelSnapshotConfig = {
    if (doc.getClassName != ModelSnapshotConfigClassName) {
      throw new IllegalArgumentException(s"The ODocument class must be '${ModelSnapshotConfigClassName}': ${doc.getClassName}")
    }
    val minTimeIntervalMillis: Long = doc.field(MinTimeInterval)
    val maxTimeIntervalMillis: Long = doc.field(MaxTimeInterval)

    ModelSnapshotConfig(
      doc.field(Enabled).asInstanceOf[Boolean],
      doc.field(TriggerByVersion).asInstanceOf[Boolean],
      doc.field(LimitedByVersion).asInstanceOf[Boolean],
      doc.field(MinVersionInterval).asInstanceOf[Long],
      doc.field(MaxVersionInterval).asInstanceOf[Long],
      doc.field(TriggerByTime).asInstanceOf[Boolean],
      doc.field(LimitedByTime).asInstanceOf[Boolean],
      Duration.ofMillis(minTimeIntervalMillis),
      Duration.ofMillis(maxTimeIntervalMillis))
  }

  val ModelSnapshotConfigClassName = "ModelSnapshotConfig"
  
  object ModelSnapshotConfigFields {
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