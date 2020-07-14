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

import com.convergencelabs.convergence.server.backend.db.schema.SchemaVersion.InvalidSchemaVersion
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

final class SchemaVersionSpec extends AnyWordSpecLike with Matchers with MockitoSugar {

  "SchemaVersion" when {
    "when testing equality" must {
      "return true for equal versions" in {
        SchemaVersion(1, 0) == SchemaVersion(1, 0) shouldBe true
      }

      "return false for different patch versions" in {
        SchemaVersion(1, 0) == SchemaVersion(1, 1) shouldBe false
      }

      "return false for different versions" in {
        SchemaVersion(2, 0) == SchemaVersion(1, 0) shouldBe false
      }

      "return false for different versions and patches" in {
        SchemaVersion(2, 0) == SchemaVersion(2, 1) shouldBe false
      }
    }

    "when evaluating less than" must {
      "return false for equal versions" in {
        SchemaVersion(1, 0) < SchemaVersion(1, 0) shouldBe false
      }

      "return true when the version is less but the patch is more" in {
        SchemaVersion(1, 0) < SchemaVersion(2, 1) shouldBe true
      }

      "return true when the version is less but the patch is equal" in {
        SchemaVersion(1, 0) < SchemaVersion(2, 0) shouldBe true
      }

      "return true when the version is less and the patch is more" in {
        SchemaVersion(1, 1) < SchemaVersion(2, 0) shouldBe true
      }

      "return true when the version is equal and the patch is less" in {
        SchemaVersion(1, 0) < SchemaVersion(1, 1) shouldBe true
      }

      "return false when the version is equal and the patch is equal" in {
        SchemaVersion(1, 0) < SchemaVersion(1, 0) shouldBe false
      }

      "return false when the version more but the patch is less" in {
        SchemaVersion(2, 0) < SchemaVersion(1, 1) shouldBe false
      }
    }

    "when evaluating greater than" must {
      "return false for equal versions" in {
        SchemaVersion(1, 0) > SchemaVersion(1, 0) shouldBe false
      }

      "return true when the version is more but the patch is less" in {
        SchemaVersion(2, 1) > SchemaVersion(1, 0) shouldBe true
      }

      "return true when the version is more but the patch is equal" in {
        SchemaVersion(2, 0) > SchemaVersion(1, 0) shouldBe true
      }

      "return true when the version is more and the patch is less" in {
        SchemaVersion(2, 0) > SchemaVersion(1, 1) shouldBe true
      }

      "return true when the version is equal and the patch is more" in {
        SchemaVersion(1, 1) > SchemaVersion(1, 0) shouldBe true
      }

      "return false when the version is equal and the patch is equal" in {
        SchemaVersion(1, 0) > SchemaVersion(1, 0) shouldBe false
      }

      "return false when the version less but the patch is more" in {
        SchemaVersion(1, 1) > SchemaVersion(2, 0) shouldBe false
      }
    }
  }

  "parsing" must {
    "parse the version correctly for a valid versions" in {
      SchemaVersion.parse("1.2") shouldBe Right(SchemaVersion(1, 2))
    }

    "return an error for an empty string" in {
      SchemaVersion.parse("") shouldBe Left(InvalidSchemaVersion(""))
    }

    "return an error for an a single integer" in {
      SchemaVersion.parse("1") shouldBe Left(InvalidSchemaVersion("1"))
    }

    "return an error for two dots" in {
      SchemaVersion.parse("1.0.0") shouldBe Left(InvalidSchemaVersion("1.0.0"))
    }

    "return an error for letters" in {
      SchemaVersion.parse("1.a") shouldBe Left(InvalidSchemaVersion("1.a"))
    }
  }

  "parsing unsafely" must {
    "parse the version correctly for a valid versions" in {
      SchemaVersion.parseUnsafe("1.2") shouldBe SchemaVersion(1, 2)
    }

    "return an error for an empty string" in {
      assertThrows[IllegalArgumentException] {
        SchemaVersion.parseUnsafe("")
      }
    }
  }
}





