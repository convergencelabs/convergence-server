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

import com.convergencelabs.convergence.server.backend.db.schema.SchemaMetaDataRepository.{FileNotFoundError, ParsingError}
import com.convergencelabs.convergence.server.backend.db.schema.delta._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

final class SchemaRepositorySpec extends AnyWordSpecLike with Matchers {

  import SchemaRepositorySpec._

  "SchemaRepository" when {
    "when loading from the class path " must {
      "successfully construct if the index file is found" in {
        new SchemaMetaDataRepository(TestBasePath)
      }
    }

    "when reading the version index" must {
      "get the correct versions if he index file is found" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        val versions = indexReader.readVersions()
        versions shouldBe Right(SchemaVersionIndex("5.0", List("1.0", "2.0", "2.1", "3.0", "3.1", "4.0", "5.0")))
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
          fail(schema.toString)
        }

        val releaseDate = LocalDate.of(2020, 6, 28)
        manifest shouldBe SchemaVersionManifest(
          released = true,
          Some(releaseDate),
          "1b2a5970d2f10979efbb73afb3ec4e2b74892883e2247e7a9270e031a82e4ba4",
          List(
            UpgradeDeltaEntry("2020_03_01_add-class-3", None, "0793f85cf3a783110337e77e31809369c1525535e54b5236b698b936de37479f"),
            UpgradeDeltaEntry("2020_06_27_make-class-2-prop1-nullable", Some("2.0"), "8b8c64e34fc11af6eb424ccf4ac95b8a5cc596772f71e840516ea5e7dcabdb3a")
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

    "when reading a delta" must {
      "successfully load a valid delta" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        val Right(UpgradeDeltaAndScript(id, delta, _)) = indexReader.readDelta(Delta_2020_03_01_add_class_3_id)
        id shouldBe Delta_2020_03_01_add_class_3_id
        delta shouldBe Delta_2020_03_01_add_class_3
      }

      "successfully load a delta with a backportTag" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        val deltaId = Delta_2020_06_27_make_class_2_prop1a_nullable_id.withTag("2.0")
        val Right(UpgradeDeltaAndScript(id, delta, _)) = indexReader.readDelta(deltaId)
        id shouldBe deltaId
        delta shouldBe Delta_2020_06_27_make_class_2_prop1a_nullable_2_0
      }

      "fail if a delta can't be loaded" in {
        val indexReader = new SchemaMetaDataRepository(TestBasePath)
        val badDelta = UpgradeDeltaId("does not exist")
        indexReader.readDelta(badDelta) shouldBe
          Left(FileNotFoundError(SchemaMetaDataRepository.resolveDeltaPath(TestBasePath, badDelta)))
      }
    }
  }
}

object SchemaRepositorySpec {
  val TestBasePath = "/schema/test-index"

  val Delta_2020_03_01_add_class_3_id: UpgradeDeltaId = UpgradeDeltaId("2020_03_01_add-class-3")
  val Delta_2020_06_27_make_class_2_prop1a_nullable_id: UpgradeDeltaId = UpgradeDeltaId("2020_06_27_make-class-2-prop1a-nullable")

  val Delta_2020_03_01_add_class_3: Delta = Delta(
    List(
      CreateClass("Class3", None, None, List(
        Property("prop1", OrientType.String, None, None, Some(Constraints(None, None, Some(true), None, Some(true), None, None, None, None))),
        Property("prop2", OrientType.Link, None, Some("Class1"), Some(Constraints(None, None, Some(true), None, Some(true), None, None, None, None)))))
    ),
    Some("Adds Class3"))

  val Delta_2020_06_27_make_class_2_prop1a_nullable: Delta = Delta(
    List(
      AddProperty("Class2", Property("prop1a", OrientType.String, None, None, Some(Constraints(None, None, Some(true), None, Some(false), None, None, None, None))))
    ),
    Some("Makes Class2.prop1a nullable")
  )

  val Delta_2020_06_27_make_class_2_prop1a_nullable_2_0: Delta = Delta(
    List(
      AddProperty("Class2", Property("prop1", OrientType.String, None, None, Some(Constraints(None, None, Some(true), None, Some(false), None, None, None, None))))
    ),
    Some("Makes Class2.prop1 nullable")
  )
}
