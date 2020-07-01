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

package com.convergencelabs.convergence.server.backend.services.domain.model

import java.time.{Duration, Instant}

import com.convergencelabs.convergence.server.model.domain.ModelSnapshotConfig

class ModelSnapshotCalculator(snapshotConfig: ModelSnapshotConfig) {
  def snapshotRequired(previousVersion: Long,
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
