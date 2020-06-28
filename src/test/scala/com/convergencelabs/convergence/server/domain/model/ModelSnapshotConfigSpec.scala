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

package com.convergencelabs.convergence.server.domain.model

import java.time.{Duration, Instant}
import java.time.temporal.ChronoUnit

import com.convergencelabs.convergence.server.domain.ModelSnapshotConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

// scalastyle:off magic.number
class ModelSnapshotConfigSpec extends AnyWordSpec with Matchers {

  private val versionBasedConfig = ModelSnapshotConfig(
    snapshotsEnabled = true,
    triggerByVersion = true,
    limitedByVersion = true,
    250,
    500,
    triggerByTime = false,
    limitedByTime = false,
    Duration.of(0, ChronoUnit.MINUTES),
    Duration.of(0, ChronoUnit.MINUTES))

  private val disabledConfig = ModelSnapshotConfig(
    snapshotsEnabled = false,
    triggerByVersion = true,
    limitedByVersion = true,
    250,
    500,
    triggerByTime = false,
    limitedByTime = false,
    Duration.of(0, ChronoUnit.MINUTES),
    Duration.of(0, ChronoUnit.MINUTES))

  private val timeBasedConfig = ModelSnapshotConfig(
    snapshotsEnabled = true,
    triggerByVersion = false,
    limitedByVersion = false,
    0,
    0,
    triggerByTime = true,
    limitedByTime = true,
    Duration.of(10, ChronoUnit.MINUTES),
    Duration.of(20, ChronoUnit.MINUTES))

  "A ModelSnapshotConfig" when {
    "snapshots are disabled" must {
      "not require snapshots" in {
        val calc = new ModelSnapshotCalculator(disabledConfig)
        calc.snapshotRequired(0L, 501L, Instant.now(), Instant.now()) shouldBe false
      }
    }

    "testing if a snapshot is required using versions only" must {
      "not require a snapshot if the version delta is less then the minimum" in {
        val calc = new ModelSnapshotCalculator(versionBasedConfig)
        calc.snapshotRequired(0L, 249L, Instant.now(), Instant.now()) shouldBe false
      }

      "not require a snapshot if the version delta is equal to the minimum" in {
        val calc = new ModelSnapshotCalculator(versionBasedConfig)
        calc.snapshotRequired(0L, 250L, Instant.now(), Instant.now()) shouldBe false
      }

      "not require a snapshot if the version delta is greater than the minimum" in {
        val calc = new ModelSnapshotCalculator(versionBasedConfig)
        calc.snapshotRequired(0L, 251L, Instant.now(), Instant.now()) shouldBe false
      }

      "not require a snapshot if the version delta is equal to the maximum" in {
        val calc = new ModelSnapshotCalculator(versionBasedConfig)
        calc.snapshotRequired(0L, 500L, Instant.now(), Instant.now()) shouldBe false
      }

      "require a snapshot if the version delta is greater than the maximum" in {
        val calc = new ModelSnapshotCalculator(versionBasedConfig)
        calc.snapshotRequired(0L, 501L, Instant.now(), Instant.now()) shouldBe true
      }
    }

    "testing if a snapshot is required using time only" must {
      "not require a snapshot if the version delta is less then the minimum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(9, ChronoUnit.MINUTES)
        val calc = new ModelSnapshotCalculator(timeBasedConfig)
        calc.snapshotRequired(0L, 0, startTime, endTime) shouldBe false
      }

      "not require a snapshot if the version delta is equal to the minimum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(10, ChronoUnit.MINUTES)
        val calc = new ModelSnapshotCalculator(timeBasedConfig)
        calc.snapshotRequired(0L, 0, startTime, endTime) shouldBe false
      }

      "not require a snapshot if the version delta is greater than the minimum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(11, ChronoUnit.MINUTES)
        val calc = new ModelSnapshotCalculator(timeBasedConfig)
        calc.snapshotRequired(0L, 0, startTime, endTime) shouldBe false
      }

      "not require a snapshot if the version delta is equal to the maximum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(20, ChronoUnit.MINUTES)
        val calc = new ModelSnapshotCalculator(timeBasedConfig)
        calc.snapshotRequired(0L, 0, startTime, endTime) shouldBe false
      }

      "require a snapshot if the time delta is greater than the maximum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(21, ChronoUnit.MINUTES)
        val calc = new ModelSnapshotCalculator(timeBasedConfig)
        calc.snapshotRequired(0L, 0, startTime, endTime) shouldBe true
      }
    }
  }

  // FIXME there are more combinations to test.
}
