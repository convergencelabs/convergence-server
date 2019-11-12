/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import java.time.Duration
import java.time.Instant

import com.convergencelabs.server.domain.ModelSnapshotConfig

class ModelSnapshotCalculator(snapshotConfig: ModelSnapshotConfig) {
  def snapshotRequired(
    previousVersion: Long,
    currentVersion: Long,
    previousTime: Instant,
    currentTime: Instant): scala.Boolean = {

    if (!snapshotConfig.snapshotsEnabled) {
      false
    } else {
      val versionInterval = currentVersion - previousVersion
      val allowedByVersion = !snapshotConfig.limitedByVersion || versionInterval >= snapshotConfig.minimumVersionInterval
      val requiredByVersion = versionInterval > snapshotConfig.maximumVersionInterval && snapshotConfig.triggerByVersion

      val timeInterval = Duration.between(previousTime, currentTime)
      val allowedByTime = !snapshotConfig.limitedByTime || timeInterval.compareTo(snapshotConfig.minimumTimeInterval) >= 0
      val requiredByTime = timeInterval.compareTo(snapshotConfig.maximumTimeInterval) > 0 && snapshotConfig.triggerByTime

      allowedByVersion && allowedByTime && (requiredByTime || requiredByVersion)
    }
  }
}
