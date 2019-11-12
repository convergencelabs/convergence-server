/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

import org.junit.runner.RunWith
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner

import com.convergencelabs.server.domain.ModelSnapshotConfig

@RunWith(classOf[JUnitRunner]) // scalastyle:off magic.number
class ModelSnapshotConfigSpec
    extends WordSpec
    with Matchers {

  val versionBasedConfig = ModelSnapshotConfig(
    true,
    true,
    true,
    250,
    500,
    false,
    false,
    Duration.of(0, ChronoUnit.MINUTES),
    Duration.of(0, ChronoUnit.MINUTES))

  val disabledConfig = ModelSnapshotConfig(
    false,
    true,
    true,
    250,
    500,
    false,
    false,
    Duration.of(0, ChronoUnit.MINUTES),
    Duration.of(0, ChronoUnit.MINUTES))

  val timeBasedConfig = ModelSnapshotConfig(
    true,
    false,
    false,
    0,
    0,
    true,
    true,
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
