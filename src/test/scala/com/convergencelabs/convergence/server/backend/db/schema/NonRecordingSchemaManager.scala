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

package com.convergencelabs.convergence.server.backend.db.schema

import java.time.Instant

import com.convergencelabs.convergence.server.backend.db.DatabaseProvider

import scala.util.{Success, Try}


/**
 * This is a help class that is used by persistence storage tests that need
 * to install a schema but don't want to record all of the delta and version
 * log entries because the convergence database isn't really there for testing.
 */
class NonRecordingSchemaManager(schemaKind: NonRecordingSchemaManager.SchemaType.Value,
                                databaseProvider: DatabaseProvider) extends SchemaManager(
  new SchemaMetaDataRepository(schemaKind match {
    case NonRecordingSchemaManager.SchemaType.Convergence =>
      ConvergenceSchemaManager.BasePath
    case NonRecordingSchemaManager.SchemaType.Domain =>
      DomainSchemaManager.BasePath
  }),
  new FakeSchemaStatePersistence(),
  new OrientDBDeltaApplicator(databaseProvider)) {
}

object NonRecordingSchemaManager {
  object SchemaType extends Enumeration {
    val Domain, Convergence = Value
  }
}

private class FakeSchemaStatePersistence extends SchemaStatePersistence {

  override def installedVersion(): Try[Option[String]] = Success(None)

  override def appliedDeltas(): Try[List[UpgradeDeltaId]] = Success(List())

  override def recordImplicitDeltasFromInstall(deltaIds: List[UpgradeDeltaId], appliedForVersion: String): Try[Unit] = Success(())

  override def recordDeltaSuccess(delta: UpgradeDeltaAndScript, appliedForVersion: String): Try[Unit] = Success(())

  override def recordDeltaFailure(delta: UpgradeDeltaAndScript, error: String, appliedForVersion: String): Unit = ()

  override def recordNewVersion(version: String, date: Instant): Try[Unit] = Success(())
}
