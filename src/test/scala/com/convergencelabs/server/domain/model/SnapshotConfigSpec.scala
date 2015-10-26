package com.convergencelabs.server.domain.model

import akka.testkit.TestKit
import org.scalatest.mock.MockitoSugar
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.mockito.Mockito
import java.time.Instant
import org.scalatest.WordSpec
import java.time.Duration
import java.time.temporal.ChronoUnit

@RunWith(classOf[JUnitRunner])
class SnapshotConfigSpec extends WordSpec {

  "A SnapshotConfig" when {
    "testing if a snapshot is required using versions only" must {
      "not require a snapshot if the version delta is less then the minimum" in {
        val snapshotConfig = SnapshotConfig(
          true,
          250,
          500,
          false,
          Duration.of(0, ChronoUnit.MINUTES),
          Duration.of(0, ChronoUnit.MINUTES))

        assert(!snapshotConfig.snapshotRequired(0L, 249L, Instant.now(), Instant.now()))
      }

      "not require a snapshot if the version delta is equal to the minimum" in {
        val snapshotConfig = SnapshotConfig(
          true,
          250,
          500,
          false,
          Duration.of(0, ChronoUnit.MINUTES),
          Duration.of(0, ChronoUnit.MINUTES))

        assert(!snapshotConfig.snapshotRequired(0L, 250L, Instant.now(), Instant.now()))
      }

      "not require a snapshot if the version delta is greater than the minimum" in {
        val snapshotConfig = SnapshotConfig(
          true,
          250,
          500,
          false,
          Duration.of(0, ChronoUnit.MINUTES),
          Duration.of(0, ChronoUnit.MINUTES))

        assert(!snapshotConfig.snapshotRequired(0L, 251L, Instant.now(), Instant.now()))
      }

      "not require a snapshot if the version delta is equal to the maximum" in {
        val snapshotConfig = SnapshotConfig(
          true,
          250,
          500,
          false,
          Duration.of(0, ChronoUnit.MINUTES),
          Duration.of(0, ChronoUnit.MINUTES))

        assert(!snapshotConfig.snapshotRequired(0L, 500L, Instant.now(), Instant.now()))
      }

      "require a snapshot if the version delta is greater than the maximum" in {
        val snapshotConfig = SnapshotConfig(
          true,
          250,
          500,
          false,
          Duration.of(0, ChronoUnit.MINUTES),
          Duration.of(0, ChronoUnit.MINUTES))

        assert(snapshotConfig.snapshotRequired(0L, 501L, Instant.now(), Instant.now()))
      }
    }

    "testing if a snapshot is required using time only" must {
      "not require a snapshot if the version delta is less then the minimum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(9, ChronoUnit.MINUTES)
        val snapshotConfig = SnapshotConfig(
          false,
          0,
          0,
          true,
          Duration.of(10, ChronoUnit.MINUTES),
          Duration.of(20, ChronoUnit.MINUTES))

        assert(!snapshotConfig.snapshotRequired(0L, 0, startTime, endTime))
      }

      "not require a snapshot if the version delta is equal to the minimum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(10, ChronoUnit.MINUTES)
        val snapshotConfig = SnapshotConfig(
          false,
          0,
          0,
          true,
          Duration.of(10, ChronoUnit.MINUTES),
          Duration.of(20, ChronoUnit.MINUTES))

        assert(!snapshotConfig.snapshotRequired(0L, 0, startTime, endTime))
      }

      "not require a snapshot if the version delta is greater than the minimum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(11, ChronoUnit.MINUTES)
        val snapshotConfig = SnapshotConfig(
          false,
          0,
          0,
          true,
          Duration.of(10, ChronoUnit.MINUTES),
          Duration.of(20, ChronoUnit.MINUTES))

        assert(!snapshotConfig.snapshotRequired(0L, 0, startTime, endTime))
      }

      "not require a snapshot if the version delta is equal to the maximum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(20, ChronoUnit.MINUTES)
        val snapshotConfig = SnapshotConfig(
          false,
          0,
          0,
          true,
          Duration.of(10, ChronoUnit.MINUTES),
          Duration.of(20, ChronoUnit.MINUTES))

        assert(!snapshotConfig.snapshotRequired(0L, 0, startTime, endTime))
      }

      "require a snapshot if the time delta is greater than the maximum" in {
        val startTime = Instant.now()
        val endTime = startTime.plus(21, ChronoUnit.MINUTES)
        val snapshotConfig = SnapshotConfig(
          false,
          0,
          0,
          true,
          Duration.of(10, ChronoUnit.MINUTES),
          Duration.of(20, ChronoUnit.MINUTES))

        assert(snapshotConfig.snapshotRequired(0L, 0, startTime, endTime))
      }
    }
  }
  
  // FIXME there are more combinations to test.
}