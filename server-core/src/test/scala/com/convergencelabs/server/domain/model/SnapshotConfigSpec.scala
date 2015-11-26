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

@RunWith(classOf[JUnitRunner])
class ModelSnapshotConfigSpec
    extends WordSpec
    with Matchers {

  "A ModelSnapshotConfig" when {
    "snapshots are disabled" must {
      "not require snapshots" in {
        val snapshotConfig = ModelSnapshotConfig(
          false,
          true,
          true,
          250,
          500,
          false,
          false,
          Duration.of(0, ChronoUnit.MINUTES),
          Duration.of(0, ChronoUnit.MINUTES))

        val calc = new ModelSnapshotCalculator(snapshotConfig)
        calc.snapshotRequired(0L, 501L, Instant.now(), Instant.now()) shouldBe false
      }
    }

    "testing if a snapshot is required using versions only" must {
      "not require a snapshot if the version delta is less then the minimum" in {
        val snapshotConfig = ModelSnapshotConfig(
          true,
          true,
          true,
          250,
          500,
          false,
          false,
          Duration.of(0, ChronoUnit.MINUTES),
          Duration.of(0, ChronoUnit.MINUTES))

        val calc = new ModelSnapshotCalculator(snapshotConfig)
        calc.snapshotRequired(0L, 249L, Instant.now(), Instant.now()) shouldBe false
      }

      "not require a snapshot if the version delta is equal to the minimum" in {
        val snapshotConfig = ModelSnapshotConfig(
          true,
          true,
          true,
          250,
          500,
          false,
          false,
          Duration.of(0, ChronoUnit.MINUTES),
          Duration.of(0, ChronoUnit.MINUTES))

        val calc = new ModelSnapshotCalculator(snapshotConfig)
        calc.snapshotRequired(0L, 250L, Instant.now(), Instant.now()) shouldBe false
      }

      "not require a snapshot if the version delta is greater than the minimum" in {
        val snapshotConfig = ModelSnapshotConfig(
          true,
          true,
          true,
          250,
          500,
          false,
          false,
          Duration.of(0, ChronoUnit.MINUTES),
          Duration.of(0, ChronoUnit.MINUTES))

        val calc = new ModelSnapshotCalculator(snapshotConfig)
        calc.snapshotRequired(0L, 251L, Instant.now(), Instant.now()) shouldBe false
      }

      "not require a snapshot if the version delta is equal to the maximum" in {
        val snapshotConfig = ModelSnapshotConfig(
          true,
          true,
          true,
          250,
          500,
          false,
          false,
          Duration.of(0, ChronoUnit.MINUTES),
          Duration.of(0, ChronoUnit.MINUTES))

        val calc = new ModelSnapshotCalculator(snapshotConfig)
        calc.snapshotRequired(0L, 500L, Instant.now(), Instant.now()) shouldBe false
      }

      "require a snapshot if the version delta is greater than the maximum" in {
        val snapshotConfig = ModelSnapshotConfig(
          true,
          true,
          true,
          250,
          500,
          false,
          false,
          Duration.of(0, ChronoUnit.MINUTES),
          Duration.of(0, ChronoUnit.MINUTES))

        val calc = new ModelSnapshotCalculator(snapshotConfig)
        calc.snapshotRequired(0L, 501L, Instant.now(), Instant.now()) shouldBe true
      }
    }

    "testing if a snapshot is required using time only" must {
      "not require a snapshot if the version delta is less then the minimum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(9, ChronoUnit.MINUTES)
        val snapshotConfig = ModelSnapshotConfig(
          true,
          false,
          false,
          0,
          0,
          true,
          true,
          Duration.of(10, ChronoUnit.MINUTES),
          Duration.of(20, ChronoUnit.MINUTES))

        val calc = new ModelSnapshotCalculator(snapshotConfig)
        calc.snapshotRequired(0L, 0, startTime, endTime) shouldBe false
      }

      "not require a snapshot if the version delta is equal to the minimum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(10, ChronoUnit.MINUTES)
        val snapshotConfig = ModelSnapshotConfig(
          true,
          false,
          false,
          0,
          0,
          true,
          true,
          Duration.of(10, ChronoUnit.MINUTES),
          Duration.of(20, ChronoUnit.MINUTES))

        val calc = new ModelSnapshotCalculator(snapshotConfig)
        calc.snapshotRequired(0L, 0, startTime, endTime) shouldBe false
      }

      "not require a snapshot if the version delta is greater than the minimum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(11, ChronoUnit.MINUTES)
        val snapshotConfig = ModelSnapshotConfig(
          true,
          false,
          false,
          0,
          0,
          true,
          true,
          Duration.of(10, ChronoUnit.MINUTES),
          Duration.of(20, ChronoUnit.MINUTES))

        val calc = new ModelSnapshotCalculator(snapshotConfig)
        calc.snapshotRequired(0L, 0, startTime, endTime) shouldBe false
      }

      "not require a snapshot if the version delta is equal to the maximum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(20, ChronoUnit.MINUTES)
        val snapshotConfig = ModelSnapshotConfig(
          true,
          false,
          false,
          0,
          0,
          true,
          true,
          Duration.of(10, ChronoUnit.MINUTES),
          Duration.of(20, ChronoUnit.MINUTES))

        val calc = new ModelSnapshotCalculator(snapshotConfig)
        calc.snapshotRequired(0L, 0, startTime, endTime) shouldBe false
      }

      "require a snapshot if the time delta is greater than the maximum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(21, ChronoUnit.MINUTES)
        val snapshotConfig = ModelSnapshotConfig(
          true,
          false,
          false,
          0,
          0,
          true,
          true,
          Duration.of(10, ChronoUnit.MINUTES),
          Duration.of(20, ChronoUnit.MINUTES))

        val calc = new ModelSnapshotCalculator(snapshotConfig)
        calc.snapshotRequired(0L, 0, startTime, endTime) shouldBe true
      }
    }
  }

  // FIXME there are more combinations to test.
}