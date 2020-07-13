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

import java.time.LocalDate

import com.convergencelabs.convergence.server.InducedTestingException
import com.convergencelabs.convergence.server.backend.db.schema.SchemaManager._
import com.convergencelabs.convergence.server.backend.db.schema.SchemaMetaDataRepository._
import com.convergencelabs.convergence.server.backend.db.schema.delta.Delta
import org.mockito
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Failure, Success}

final class SchemaManagerSpec extends AnyWordSpecLike with Matchers with MockitoSugar {

  import SchemaManagerSpec._

  "SchemaManager" when {
    "installing a fresh schema to the latest version" must {
      "apply and record the correct deltas " in {
        val metaDataRepo = mockRepoV1()

        val deltaApplicator = mock[DeltaApplicator]
        Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
          .thenReturn(Success(()))

        val installDeltas = List(
          UpgradeDeltaId(SchemaManager.InstallDeltaReservedName, Some(SchemaManager.InstallDeltaReservedTag)),
          InstallImplicitDelta.toDeltaId.withTag(SchemaManager.InstallDeltaReservedTag))

        val schemaStatePersistence = mock[SchemaStatePersistence]
        Mockito.when(schemaStatePersistence
          .recordImplicitDeltasFromInstall(installDeltas, Version_1_0))
          .thenReturn(Success(()))

        Mockito.when(schemaStatePersistence
          .recordNewVersion(mockito.Matchers.any(), mockito.Matchers.any()))
          .thenReturn(Success(()))

        val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

        schemaManager.install() shouldBe Right(())

        verify(deltaApplicator, times(1))
          .applyDeltaToSchema(MockDelta)

        verify(schemaStatePersistence, times(1))
          .recordImplicitDeltasFromInstall(installDeltas, Version_1_0)

        verify(schemaStatePersistence, times(1))
          .recordNewVersion(mockito.Matchers.eq(Version_1_0), mockito.Matchers.any())
      }

      "return a repository error if reading the current version fails" in {
        val metaDataRepo = mockRepoV1()
        Mockito.when(metaDataRepo.readVersions())
          .thenReturn(Left(InducedParsingError))

        val deltaApplicator = mock[DeltaApplicator]
        Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
          .thenReturn(Success(()))

        val schemaStatePersistence = mock[SchemaStatePersistence]
        Mockito.when(schemaStatePersistence
          .recordImplicitDeltasFromInstall(List(InstallImplicitDelta.toDeltaId), Version_1_0))
          .thenReturn(Success(()))

        val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

        val Left(error) = schemaManager.install()
        error shouldBe a[RepositoryError]
      }

      "return a repository error if reading the version manifest" in {
        val metaDataRepo = mockRepoV1()
        Mockito.when(metaDataRepo.readSchemaVersionManifest(Version_1_0))
          .thenReturn(Left(InducedParsingError))

        val deltaApplicator = mock[DeltaApplicator]
        Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
          .thenReturn(Success(()))

        val schemaStatePersistence = mock[SchemaStatePersistence]
        Mockito.when(schemaStatePersistence
          .recordImplicitDeltasFromInstall(List(InstallImplicitDelta.toDeltaId), Version_1_0))
          .thenReturn(Success(()))

        val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

        val Left(error) = schemaManager.install()
        error shouldBe a[RepositoryError]
      }

      "return a repository error if reading the full schema delta" in {
        val metaDataRepo = mockRepoV1()
        Mockito.when(metaDataRepo.readFullSchema(Version_1_0))
          .thenReturn(Left(InducedParsingError))

        val deltaApplicator = mock[DeltaApplicator]
        Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
          .thenReturn(Success(()))

        val schemaStatePersistence = mock[SchemaStatePersistence]
        Mockito.when(schemaStatePersistence.recordImplicitDeltasFromInstall(List(InstallImplicitDelta.toDeltaId), Version_1_0))
          .thenReturn(Success(()))

        val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

        val Left(error) = schemaManager.install()
        error shouldBe a[RepositoryError]
      }

      "return a delta application error if recording the delta fails" in {
        val metaDataRepo = mockRepoV1()

        val deltaApplicator = mock[DeltaApplicator]
        Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
          .thenReturn(Success(()))

        val schemaStatePersistence = mock[SchemaStatePersistence]
        Mockito.when(schemaStatePersistence.recordImplicitDeltasFromInstall(List(InstallImplicitDelta.toDeltaId), Version_1_0))
          .thenReturn(Failure(InducedTestingException()))

        val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

        val Left(error) = schemaManager.install()
        error shouldBe a[DeltaApplicationError]
      }

      "return a repository error if a repository file is not found" in {
        val metaDataRepo = mockRepoV1()
        Mockito.when(metaDataRepo.readVersions())
          .thenReturn(Left(FileNotFoundError("path")))

        val deltaApplicator = mock[DeltaApplicator]
        Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
          .thenReturn(Success(()))

        val schemaStatePersistence = mock[SchemaStatePersistence]
        Mockito.when(schemaStatePersistence.recordImplicitDeltasFromInstall(List(InstallImplicitDelta.toDeltaId), Version_1_0))
          .thenReturn(Success(()))

        val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

        val Left(error) = schemaManager.install()
        error shouldBe a[RepositoryError]
      }

      "return a repository error if an unknown repository error occurs" in {
        val metaDataRepo = mockRepoV1()
        Mockito.when(metaDataRepo.readVersions())
          .thenReturn(Left(UnknownError))

        val deltaApplicator = mock[DeltaApplicator]
        Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
          .thenReturn(Success(()))

        val schemaStatePersistence = mock[SchemaStatePersistence]
        Mockito.when(schemaStatePersistence.recordImplicitDeltasFromInstall(List(InstallImplicitDelta.toDeltaId), Version_1_0))
          .thenReturn(Success(()))

        val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

        val Left(error) = schemaManager.install()
        error shouldBe a[RepositoryError]
      }

      "return a delta validation error if the hash fails" in {
        val metaDataRepo = mockRepoV1()
        Mockito.when(metaDataRepo.readSchemaVersionManifest(Version_1_0))
          .thenReturn(Right(VersionManifest_1_0.copy(schemaSha256 = "bad")))

        val deltaApplicator = mock[DeltaApplicator]
        Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
          .thenReturn(Success(()))

        val schemaStatePersistence = mock[SchemaStatePersistence]
        Mockito.when(schemaStatePersistence.recordImplicitDeltasFromInstall(List(InstallImplicitDelta.toDeltaId), Version_1_0))
          .thenReturn(Success(()))

        val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

        val Left(error) = schemaManager.install()
        error shouldBe a[DeltaValidationError]
      }
    }
  }

  "upgrading an existing schema" must {
    "apply and record the correct deltas" in {
      val metaDataRepo = mockRepoV3()

      val deltaApplicator = mock[DeltaApplicator]
      Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
        .thenReturn(Success(()))

      val schemaStatePersistence = mock[SchemaStatePersistence]
      Mockito.when(schemaStatePersistence.installedVersion())
        .thenReturn(Success(Some(Version_1_0)))

      Mockito.when(schemaStatePersistence.appliedDeltas())
        .thenReturn(Success(List(InstallImplicitDelta.toDeltaId)))

      Mockito.when(schemaStatePersistence.recordDeltaSuccess(Delta1, Version_3_0))
        .thenReturn(Success(()))

      Mockito.when(schemaStatePersistence.recordDeltaSuccess(Delta2, Version_3_0))
        .thenReturn(Success(()))

      Mockito.when(schemaStatePersistence
        .recordNewVersion(mockito.Matchers.any(), mockito.Matchers.any()))
        .thenReturn(Success(()))

      val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

      schemaManager.upgrade() shouldBe Right(())

      verify(deltaApplicator, times(2))
        .applyDeltaToSchema(MockDelta)

      verify(schemaStatePersistence, times(1))
        .recordDeltaSuccess(Delta1, Version_3_0)

      verify(schemaStatePersistence, times(1))
        .recordDeltaSuccess(Delta2, Version_3_0)

      verify(schemaStatePersistence, times(1))
        .recordNewVersion(mockito.Matchers.eq(Version_3_0), mockito.Matchers.any())
    }

    "return a state persistence error if the applied deltas can't be determined" in {
      val metaDataRepo = mockRepoV3()

      val deltaApplicator = mock[DeltaApplicator]
      Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
        .thenReturn(Success(()))

      val schemaStatePersistence = mock[SchemaStatePersistence]
      Mockito.when(schemaStatePersistence.installedVersion())
        .thenReturn(Success(Some(Version_1_0)))

      Mockito.when(schemaStatePersistence.appliedDeltas())
        .thenReturn(Failure(InducedTestingException()))

      val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

      val Left(error) = schemaManager.upgrade()
      error shouldBe a[StatePersistenceError]
    }

    "return a repository error if reading a delta fails" in {
      val metaDataRepo = mockRepoV3()
      Mockito.when(metaDataRepo.readDelta(Delta1Entry.toDeltaId))
        .thenReturn(Left(InducedParsingError))

      val deltaApplicator = mock[DeltaApplicator]
      Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
        .thenReturn(Success(()))

      val schemaStatePersistence = mock[SchemaStatePersistence]
      Mockito.when(schemaStatePersistence.installedVersion())
        .thenReturn(Success(Some(Version_1_0)))

      Mockito.when(schemaStatePersistence.appliedDeltas())
        .thenReturn(Success(List(InstallImplicitDelta.toDeltaId)))

      val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

      val Left(error) = schemaManager.upgrade()
      error shouldBe a[RepositoryError]
    }

    "return a delta application failure and record the failure if applying a delta fails" in {
      val metaDataRepo = mockRepoV3()

      val deltaApplicator = mock[DeltaApplicator]
      Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
        .thenReturn(Failure(InducedTestingException()))

      val schemaStatePersistence = mock[SchemaStatePersistence]
      Mockito.when(schemaStatePersistence.installedVersion())
        .thenReturn(Success(Some(Version_1_0)))

      Mockito.when(schemaStatePersistence.appliedDeltas())
        .thenReturn(Success(List(InstallImplicitDelta.toDeltaId)))

      Mockito.when(schemaStatePersistence.recordDeltaSuccess(Delta1, Version_1_0))
        .thenReturn(Success(()))

      Mockito.when(schemaStatePersistence.recordDeltaSuccess(Delta2, Version_1_0))
        .thenReturn(Success(()))

      val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

      schemaManager.upgrade() shouldBe Left(DeltaApplicationError())

      verify(deltaApplicator, times(1))
        .applyDeltaToSchema(MockDelta)

      verify(schemaStatePersistence, times(1))
        .recordDeltaFailure(mockito.Matchers.eq(Delta1), mockito.Matchers.any(), mockito.Matchers.eq(Version_3_0))
    }

    "return a state persistence error if recording a delta fails" in {
      val metaDataRepo = mockRepoV3()

      val deltaApplicator = mock[DeltaApplicator]
      Mockito.when(deltaApplicator.applyDeltaToSchema(MockDelta))
        .thenReturn(Success(()))

      val schemaStatePersistence = mock[SchemaStatePersistence]
      Mockito.when(schemaStatePersistence.installedVersion())
        .thenReturn(Success(Some(Version_1_0)))

      Mockito.when(schemaStatePersistence.appliedDeltas())
        .thenReturn(Success(List(InstallImplicitDelta.toDeltaId)))

      Mockito.when(schemaStatePersistence.recordDeltaSuccess(Delta1, Version_3_0))
        .thenReturn(Failure(InducedTestingException()))

      val schemaManager = new SchemaManager(metaDataRepo, schemaStatePersistence, deltaApplicator)

      val Left(error) = schemaManager.upgrade()
      error shouldBe a[DeltaApplicationError]
    }
  }
}

object SchemaManagerSpec extends MockitoSugar {
  val Version_1_0 = "1.0"

  val DeltaScript = "actions: []"
  val DeltaHash = "51edb047a65a05ca6910fdb8cc40bd1e63b0db0a782ba48cfae87f405d5e36fe"
  val MockDelta: Delta = Delta(List(), None)

  val InstallImplicitDelta: UpgradeDeltaEntry =
    UpgradeDeltaEntry("_implicit_delta", None, "")


  val VersionManifest_1_0: SchemaVersionManifest = SchemaVersionManifest(
    released = true,
    Some(LocalDate.of(2020, 6, 20)),
    DeltaHash,
    List(InstallImplicitDelta)
  )

  val InstallSchema_1_0: InstallDeltaAndScript =
    InstallDeltaAndScript(Version_1_0, MockDelta, DeltaScript)

  val InducedParsingError: ParsingError = ParsingError("induced")

  val Delta1Entry: UpgradeDeltaEntry =
    UpgradeDeltaEntry("delta1", None, DeltaHash)

  val Delta2Entry: UpgradeDeltaEntry =
    UpgradeDeltaEntry("delta2", None, DeltaHash)

  val Version_2_0 = "2.0"
  val Version_3_0 = "3.0"

  val Delta1: UpgradeDeltaAndScript = UpgradeDeltaAndScript(Delta1Entry.toDeltaId, MockDelta, DeltaScript)
  val Delta2: UpgradeDeltaAndScript = UpgradeDeltaAndScript(Delta2Entry.toDeltaId, MockDelta, DeltaScript)

  val VersionManifest_3_0: SchemaVersionManifest = SchemaVersionManifest(
    released = true,
    Some(LocalDate.of(2020, 7, 20)),
    DeltaHash,
    List(InstallImplicitDelta, Delta1Entry, Delta2Entry)
  )

  def mockRepoV1(): SchemaMetaDataRepository = {
    val metaDataRepo = mock[SchemaMetaDataRepository]
    Mockito.when(metaDataRepo.readVersions())
      .thenReturn(Right(SchemaVersionIndex(Version_1_0, List(Version_1_0))))
    Mockito.when(metaDataRepo.readSchemaVersionManifest(Version_1_0))
      .thenReturn(Right(VersionManifest_1_0))
    Mockito.when(metaDataRepo.readFullSchema(Version_1_0))
      .thenReturn(Right(InstallSchema_1_0))

    metaDataRepo
  }

  def mockRepoV3(): SchemaMetaDataRepository = {
    val metaDataRepo = mock[SchemaMetaDataRepository]
    Mockito.when(metaDataRepo.readVersions())
      .thenReturn(Right(SchemaVersionIndex(Version_3_0, List(Version_1_0, Version_2_0, Version_3_0))))
    Mockito.when(metaDataRepo.readSchemaVersionManifest(Version_3_0))
      .thenReturn(Right(VersionManifest_3_0))
    Mockito.when(metaDataRepo.readDelta(Delta1Entry.toDeltaId))
      .thenReturn(Right(Delta1))
    Mockito.when(metaDataRepo.readDelta(Delta2Entry.toDeltaId))
      .thenReturn(Right(Delta2))

    metaDataRepo
  }
}



