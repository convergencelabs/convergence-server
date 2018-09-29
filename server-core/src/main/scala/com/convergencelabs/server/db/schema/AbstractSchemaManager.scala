package com.convergencelabs.server.db.schema

import scala.util.Failure
import scala.util.Try

import com.orientechnologies.orient.core.db.document.ODatabaseDocument

import grizzled.slf4j.Logging

abstract class AbstractSchemaManager(db: ODatabaseDocument, preRelease: Boolean) extends Logging {

  def install(): Try[Unit] = {
    loadManifest().flatMap { manifest =>
      executeInstall(manifest, manifest.maxVersion(preRelease))
    }
  }

  def instatll(version: Int): Try[Unit] = {
    loadManifest().flatMap { executeInstall(_, version) }
  }

  private[this] def executeInstall(manifest: DeltaManifest, version: Int): Try[Unit] = {
    val max = manifest.maxVersion(preRelease)
    if (version > max) {
      Failure(new IllegalArgumentException(s"Invalid version ${version}, which is greater than the max: ${max}"))
    } else {
      manifest.getFullDelta(version) flatMap { applyDelta(_) }
    }
  }

  def upgrade(): Try[Unit] = {
    loadManifest().flatMap { manifest =>
      executeUpgrade(manifest, manifest.maxVersion(preRelease))
    }
  }

  def upgrade(version: Int): Try[Unit] = {
    loadManifest().flatMap(executeUpgrade(_, version))
  }

  private[this] def executeUpgrade(manifest: DeltaManifest, version: Int): Try[Unit] = {
    logger.debug(s"Executing database upgrade to version: ${version}")
    getCurrentVersion() flatMap { currentVersion =>
      if (version <= currentVersion) {
        Failure(new IllegalArgumentException(
          s"Invalid version ${version}, which is less than or equal to the current version: ${currentVersion}"))
      } else {
        logger.debug(s"Executing database upgrade from ${currentVersion} to ${version}")
        Try {
          for (
            v <- (currentVersion + 1) to version
          ) yield (
            manifest.getIncrementalDelta(v).flatMap(applyDelta(_)).get
          )
        }
      }
    }
  }

  def applyDelta(delta: DeltaScript): Try[Unit] = {
    logger.debug(s"Applying delta: ${delta.delta.version}")
    DatabaseDeltaProcessor.apply(delta.delta, db) recoverWith {
      case cause: Throwable =>
        logger.error(s"Delta ${delta.delta.version} failed", cause)
        recordDeltaFailure(delta, cause)
        Failure(cause)
    } flatMap { _ =>
      logger.debug(s"Delta ${delta.delta.version} applied successfully.")
      recordDeltaSuccess(delta) recoverWith {
        case cause: Throwable =>
          logger.error(s"Storing Delta ${delta.delta.version} Success failed", cause)
          Failure(cause)
      }
    }
  }

  def getCurrentVersion(): Try[Int]

  def recordDeltaSuccess(delta: DeltaScript): Try[Unit]

  def recordDeltaFailure(delta: DeltaScript, cause: Throwable): Unit

  def loadManifest(): Try[DeltaManifest]
}
