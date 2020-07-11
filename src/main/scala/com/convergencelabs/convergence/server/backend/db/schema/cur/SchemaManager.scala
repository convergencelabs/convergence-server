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

package com.convergencelabs.convergence.server.backend.db.schema.cur

import com.convergencelabs.convergence.server.backend.db.schema.cur.SchemaMetaDataRepository.{ParsingError, ReadError, UnknownError}
import com.convergencelabs.convergence.server.util.{ExceptionUtils, Sha256HashValidator}
import grizzled.slf4j.Logging

import scala.util.{Failure, Success}

/**
 * The [[SchemaManager]] is the implements the core logic for installing and
 * upgrading schemas. It leverages the [[SchemaMetaDataRepository]] class as
 * its source of information of what versions are installable and upgradable
 * too.
 *
 * @param schemaMetaDataRepository The repository containing information on
 *                                 what versions are available.
 * @param schemaStatePersistence   Provides information on what deltas have
 *                                 been applied to the schema and allows
 *                                 the recording of deltas as they are
 *                                 applied.
 * @param deltaApplicator          A delegate that actually applies deltas
 *                                 to the schema
 */
private[schema] class SchemaManager(schemaMetaDataRepository: SchemaMetaDataRepository,
                                    schemaStatePersistence: SchemaStatePersistence,
                                    deltaApplicator: DeltaApplicator) extends Logging {

  import SchemaManager._

  /**
   * Installs the latest schema into a fresh database instance.
   *
   * @return Right if successful, or a Left(error) if unsuccessful.
   */
  def install(): Either[SchemaInstallError, Unit] = {
    for {
      versions <- readVersionIndex
      manifest <- readSchemaManifest(versions.currentVersion)
      schemaDelta <- readSchemaDelta(versions.currentVersion)
      _ <- validateHash(manifest.schemaSha256, schemaDelta.script)
      _ <- installSchema(manifest, schemaDelta)
    } yield ()
  }

  /**
   * Upgrades an existing schema to the latest version.
   *
   * @return Right if successful, or a Left(error) if unsuccessful.
   */
  def upgrade(): Either[SchemaUpgradeError, Unit] = {
    for {
      versions <- readVersionIndex
      manifest <- readSchemaManifest(versions.currentVersion)
      appliedDeltas <- appliedDeltas()
      neededDeltaIds = computeNeededDeltas(appliedDeltas.map(_.id), manifest.deltas)
      deltas <- readAndValidateDeltas(neededDeltaIds)
      _ <- applyDeltas(deltas)
    } yield ()
  }

  private[this] def installSchema(manifest: SchemaVersionManifest, schemaDelta: InstallDeltaAndScript): Either[DeltaApplicationError, Unit] = {
    (for {
      _ <- deltaApplicator.applyDeltaToSchema(schemaDelta.delta)
      _ <- schemaStatePersistence.recordImplicitDeltasFromInstall(manifest.deltas)
    } yield Right(()))
      .recoverWith { cause =>
        error("Error installing schema", cause)
        Failure(cause)
      }
      .getOrElse(Left(DeltaApplicationError()))
  }

  private[this] def computeNeededDeltas(currentDeltas: List[String], desiredDeltas: List[UpgradeDeltaEntry]): List[UpgradeDeltaEntry] =
    desiredDeltas.
      filter(d => !currentDeltas.contains(d.deltaId))

  private[this] def applyDeltas(deltas: List[UpgradeDeltaAndScript]): Either[DeltaApplicationError, Unit] = {
    val iter = deltas.iterator
    var cur: Either[DeltaApplicationError, Unit] = Right(())
    while (iter.hasNext && cur.isRight) {
      cur = applyDelta(iter.next())
    }
    cur
  }

  private[this] def applyDelta(delta: UpgradeDeltaAndScript): Either[DeltaApplicationError, Unit] = {
    deltaApplicator.applyDeltaToSchema(delta.delta) match {
      case Failure(exception) =>
        recordDeltaFailure(delta, exception)
        Left(DeltaApplicationError())
      case Success(_) =>
        logger.debug(s"Delta '${delta.id}' applied successfully")
        recordDeltaSuccess(delta).left.map(_ => DeltaApplicationError())
    }
  }



  private[this] def recordDeltaFailure(delta: UpgradeDeltaAndScript, cause: Throwable): Unit = {
    logger.error(s"Error applying Delta '${delta.id}'", cause)
    val error = ExceptionUtils.stackTraceToString(cause)
    schemaStatePersistence.recordDeltaFailure(delta, error)
  }

  private[this] def recordDeltaSuccess(delta: UpgradeDeltaAndScript): Either[StatePersistenceError, Unit] = {
    schemaStatePersistence.recordDeltaSuccess(delta) match {
      case Failure(cause) =>
        logger.error(s"Storing Delta '${delta.id}' failed", cause)
        Left(StatePersistenceError())
      case Success(_) =>
        Right(())
    }
  }

  private[this] def readAndValidateDeltas(neededDeltas: List[UpgradeDeltaEntry]): Either[SchemaUpgradeError, List[UpgradeDeltaAndScript]] = {
    val iter = neededDeltas.iterator
    var result: Either[SchemaUpgradeError, List[UpgradeDeltaAndScript]] = Right(List())
    while (iter.hasNext && result.isRight) {
      val entry = iter.next()
      result = for {
        delta <- readDelta(entry.toDeltaId)
        _ <- validateHash(entry.sha256, delta.script)
        updated <- result.map { currentList =>
          currentList :+ delta
        }
      } yield updated
    }

    result
  }

  private[this] def readVersionIndex: Either[RepositoryError, SchemaVersionIndex] =
    schemaMetaDataRepository
      .readVersions()
      .left.map(e => mapReadError(e, "version index"))

  private[this] def readSchemaManifest(version: String): Either[RepositoryError, SchemaVersionManifest] =
    schemaMetaDataRepository
      .readSchemaVersionManifest(version)
      .left.map(e => mapReadError(e, "schema version manifest"))

  private[this] def readSchemaDelta(version: String): Either[RepositoryError, InstallDeltaAndScript] =
    schemaMetaDataRepository.readFullSchema(version)
      .left.map(e => mapReadError(e, "schema installation delta"))

  private[this] def readDelta(deltaId: UpgradeDeltaId): Either[RepositoryError, UpgradeDeltaAndScript] =
    schemaMetaDataRepository.readDelta(deltaId)
      .left.map(e => mapReadError(e, s"delta $deltaId"))

  private[this] def validateHash(expectedHash: String, script: String): Either[DeltaValidationError, Unit] =
    Sha256HashValidator
      .validateHash(script, expectedHash)
      .left.map(error => InvalidHashError(error.expected, error.actual))

  private[this] def appliedDeltas(): Either[StatePersistenceError, List[UpgradeDeltaId]] = {
    schemaStatePersistence
      .appliedDeltas()
      .map(Right(_))
      .recover { cause =>
        error("could not get the applied deltas", cause)
        Left(StatePersistenceError())
      }
      .getOrElse(Left(StatePersistenceError()))
  }

  private[this] def mapReadError(readError: ReadError, whatWasRead: String): RepositoryError =
    readError match {
      case SchemaMetaDataRepository.FileNotFoundError(path) =>
        RepositoryError(s"The $whatWasRead was not found: " + path)
      case ParsingError(message) =>
        RepositoryError(s"The $whatWasRead could not be parsed: " + message)
      case UnknownError =>
        RepositoryError(s"An unknown error occurred reading the $whatWasRead.")
    }
}

object SchemaManager {

  sealed trait SchemaInstallError

  sealed trait SchemaUpgradeError

  final case class DeltaApplicationError() extends SchemaInstallError with SchemaUpgradeError

  sealed trait DeltaValidationError extends SchemaInstallError with SchemaUpgradeError

  final case class RepositoryError(message: String) extends SchemaInstallError with SchemaUpgradeError

  final case class InvalidHashError(expected: String, actual: String) extends DeltaValidationError

  final case class StatePersistenceError() extends SchemaInstallError with SchemaUpgradeError

}
