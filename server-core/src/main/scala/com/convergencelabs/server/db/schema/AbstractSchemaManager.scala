package com.convergencelabs.server.db.schema

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.datastore.DeltaHistoryStore
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.datastore.DeltaHistoryStore
import grizzled.slf4j.Logging

abstract class AbstractSchemaManager(db: ODatabaseDocumentTx, preRelease: Boolean) extends Logging {

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
      Failure(new IllegalArgumentException(
        s"Invalid version ${version}, which is greater than the max: ${max}"))
    } else {
      Success(())
    } flatMap { _ =>
      manifest.getFullDelta(version) flatMap { applyDelta(_) }
    }
  }

  def upgrade(): Try[Unit] = Try {
    loadManifest().flatMap { manifest =>
      executeUpgrade(manifest, manifest.maxVersion(preRelease))
    }
  }

  def upgrade(version: Int): Try[Unit] = Try {
    loadManifest().flatMap { executeUpgrade(_, version) }
  }

  private[this] def executeUpgrade(manifest: DeltaManifest, version: Int): Try[Unit] = {
    logger.debug(s"Executing database upgrade to version: ${version}")
    getCurrentVersion() flatMap { currentVersion =>
      if (version <= currentVersion) {
        Failure(new IllegalArgumentException(
          s"Invalid version ${version}, which is less than or equal to the current version: ${currentVersion}"))
      } else {
        Success(currentVersion)
      }
    } flatMap { currentVersion =>
      logger.debug(s"Executing database upgrade from ${currentVersion} to ${version}")
      Try {
        for {
          v <- (currentVersion + 1) to version
        } yield {
          manifest.getIncrementalDelta(v).flatMap(applyDelta(_)).get
        }
      }
    }
  }

  def applyDelta(delta: DeltaScript): Try[Unit] = {
    logger.debug(s"Applying delta: ${delta.delta.version}")
    DatabaseDeltaProcessor.apply(delta.delta, db) recoverWith {
      case cause: Exception =>
        recordDeltaFailure(delta, cause)
        Failure(cause)
    } flatMap { _ =>
      recordDeltaSuccess(delta)
    }
  }

  def getCurrentVersion(): Try[Int]

  def recordDeltaSuccess(delta: DeltaScript): Try[Unit]

  def recordDeltaFailure(delta: DeltaScript, cause: Exception): Unit

  def loadManifest(): Try[DeltaManifest]
}
