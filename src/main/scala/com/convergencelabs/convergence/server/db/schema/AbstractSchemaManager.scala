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

package com.convergencelabs.convergence.server.db.schema

import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import grizzled.slf4j.Logging

import scala.util.{Failure, Try}

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
        logger.error(s"Error applying Delta ${delta.delta.version}", cause)
        recordDeltaFailure(delta, cause)
        Failure(cause)
    } flatMap { _ =>
      logger.debug(s"Delta ${delta.delta.version} applied successfully")
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
