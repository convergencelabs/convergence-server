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

import java.time.LocalDate

import com.convergencelabs.convergence.server.backend.db.schema.cur.SchemaMetaDataRepository.{FileNotFoundError, ParsingError}
import com.convergencelabs.convergence.server.backend.db.schema.cur.delta._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

final class SchemaRepositorySpec extends AnyWordSpecLike with Matchers {

  import SchemaRepositorySpec._

  "SchemaIndexReader" when {
    "when loading from the class path " must {
      "successfully construct if the index file is found" in {
        new SchemaMetaDataRepository(TestBasePath)
      }
    }

    "when reading the version index" must {
      "get the correct versions if he index file is found" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        val versions = indexReader.readVersions()
        versions shouldBe Right(List("1.0", "2.0", "2.1", "3.0", "3.1", "4.0", "5.0"))
      }

      "return a failure if the index file is no found" in {
        val path = "/schema/test-index1"
        val indexReader = new SchemaMetaDataRepository(path)
        indexReader.readVersions() shouldBe Left(FileNotFoundError(SchemaMetaDataRepository.resolveIndexPath(path)))
      }

      "return a failure if the index file is not parsable" in {
        val indexReader = new SchemaMetaDataRepository("/schema/bad-index")
        indexReader.readVersions() match {
          case Left(ParsingError(_)) =>
          case _ =>
            fail("Expected a ParsingError")
        }
      }
    }

    "when reading a schema version manifest" must {
      "get the correct versions if he index file is found" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        val schema = indexReader.readSchemaVersionManifest("2.1")
        val manifest = schema.getOrElse {
          fail("failed to parse schema version manifest")
        }

        val releaseDate = LocalDate.of(2020, 6, 28)
        manifest shouldBe SchemaVersionManifest(
          released = true,
          Some(releaseDate),
          Some("fixsha"),
          List(
            DeltaEntry("2020_01_01_initial-schema", Some("1"), None),
            DeltaEntry("2020_03_01_add-class-3", Some("2"), None),
            DeltaEntry("2020_06_27_make-class-2-prop1-nullable", Some("3"), Some(true))
          )
        )
      }

      "return a failure if the index file is no found" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        indexReader.readSchemaVersionManifest("2.2") shouldBe
          Left(FileNotFoundError(SchemaMetaDataRepository.resolveVersionPath(TestBasePath, "2.2")))
      }
    }

    "when reading a schema" must {
      "get the correct versions if he index file is found" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        val schema = indexReader.readFullSchema("2.1")
        schema.getOrElse {
          fail("oops")
        }
      }
    }

    "when reading deltas" must {
      "successfully load no deltas" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        indexReader.readDeltas(List()) shouldBe Right(List())
      }

      "successfully load a single delta" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        val Right(List(DeltaAndRaw(delta, _))) = indexReader.readDeltas(List(Delta_2020_03_01_add_class_3_id))
        delta shouldBe Delta_2020_03_01_add_class_3
      }

      "successfully load multiple deltas" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        val Right(List(DeltaAndRaw(delta1, _), DeltaAndRaw(delta2, _))) =
          indexReader.readDeltas(List(Delta_2020_03_01_add_class_3_id, Delta_2020_06_27_make_class_2_prop1_nullable_id))
        delta1 shouldBe Delta_2020_03_01_add_class_3
        delta2 shouldBe Delta_2020_06_27_make_class_2_prop1_nullable
      }

      "fail if any delta can't be loaded" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        val badDelta = "does not exist"
        indexReader.readDeltas(List(Delta_2020_03_01_add_class_3_id, badDelta)) shouldBe
          Left(FileNotFoundError(SchemaMetaDataRepository.resolveDeltaPath(TestBasePath, badDelta)))
      }
    }
  }
}

object SchemaRepositorySpec {
  val TestBasePath = "/schema/test-index"

  val Delta_2020_03_01_add_class_3_id = "2020_03_01_add-class-3"
  val Delta_2020_06_27_make_class_2_prop1_nullable_id = "2020_06_27_make-class-2-prop1-nullable"

  val Delta_2020_03_01_add_class_3: Delta = Delta(
    List(
      CreateClass("Class3", None, None, List(
        Property("prop1", OrientType.String, None, None, Some(Constraints(None, None, Some(true), None, Some(true), None, None, None, None))),
        Property("prop2", OrientType.Link, None, Some("Class1"), Some(Constraints(None, None, Some(true), None, Some(true), None, None, None, None)))))
    ),
    Some("Adds Class3"))

  val Delta_2020_06_27_make_class_2_prop1_nullable: Delta = Delta(
    List(
      AddProperty("Class2", Property("prop1", OrientType.String, None, None, Some(Constraints(None, None, Some(true), None, Some(false), None, None, None, None))))
    ),
    Some("Makes Class2.prop1 nullable")
  )
}
