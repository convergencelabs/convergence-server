/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper

import com.convergencelabs.convergence.server.backend.datastore.ODocumentMapper
import com.convergencelabs.convergence.server.model.domain
import com.convergencelabs.convergence.server.model.domain.{CollectionConfig, ModelSnapshotConfig}
import com.orientechnologies.orient.core.record.impl.ODocument

import java.time.Duration

object ModelSnapshotConfigMapper extends ODocumentMapper {

  private[domain] def modelSnapshotConfigToODocument(snapshotConfig: ModelSnapshotConfig): ODocument = {
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

  private[domain] def oDocumentToModelSnapshotConfig(doc: ODocument): ModelSnapshotConfig = {
    validateDocumentClass(doc, DocumentClassName)

    val minTimeIntervalMillis: Long = doc.field(Fields.MinTimeInterval)
    val maxTimeIntervalMillis: Long = doc.field(Fields.MaxTimeInterval)

    domain.ModelSnapshotConfig(
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
    val TriggerByVersion = "triggerByVersion"
    val LimitedByVersion = "limitedByVersion"
    val MinVersionInterval = "minVersionInterval"
    val MaxVersionInterval = "maxVersionInterval"
    val TriggerByTime = "triggerByTime"
    val LimitedByTime = "limitedByTime"
    val MinTimeInterval = "minTimeInterval"
    val MaxTimeInterval = "maxTimeInterval"
  }
}
