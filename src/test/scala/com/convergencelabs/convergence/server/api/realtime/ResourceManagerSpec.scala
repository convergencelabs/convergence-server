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

package com.convergencelabs.convergence.server.api.realtime

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ResourceManagerSpec extends AnyWordSpecLike with Matchers {
  private val id1 = "id1"
  private val id2 = "id2"

  "A ResourceManager" when {
    "claiming a resource" must {
      "return the same resource for the same id" in {
        val resourceManager = new ResourceManager[String]()
        val r1 = resourceManager.getOrAssignResource(id1)
        val r2 = resourceManager.getOrAssignResource(id1)
        r1 shouldEqual r2
      }

      "return different resources for the different id" in {
        val resourceManager = new ResourceManager[String]()
        val r1 = resourceManager.getOrAssignResource(id1)
        val r2 = resourceManager.getOrAssignResource(id2)
        r1 should not equal r2
      }
    }

    "determining if a resource is registered" must {
      "return true if the resource is registered" in {
        val resourceManager = new ResourceManager[String]()
        val r1 = resourceManager.getOrAssignResource(id1)
        resourceManager.hasResource(r1) shouldBe true
      }

      "return false if the resource is not registered" in {
        val resourceManager = new ResourceManager[String]()
        val r1 = resourceManager.getOrAssignResource(id1)
        resourceManager.hasResource(r1 + 10) shouldBe false
      }
    }

    "determining if a id is registered" must {
      "return true if the resource is registered" in {
        val resourceManager = new ResourceManager[String]()
        val r1 = resourceManager.getOrAssignResource(id1)
        resourceManager.hasId(id1) shouldBe true
      }

      "return false if the resource is not registered" in {
        val resourceManager = new ResourceManager[String]()
        resourceManager.getOrAssignResource(id1)
        resourceManager.hasId(id2) shouldBe false
      }
    }

    "getting the id for a resource" must {
      "return the correct id if the resource is registered" in {
        val resourceManager = new ResourceManager[String]()
        val r1 = resourceManager.getOrAssignResource(id1)
        resourceManager.getId(r1) shouldBe Some(id1)
      }

      "return None if the resource is not registered" in {
        val resourceManager = new ResourceManager[String]()
        val r1 = resourceManager.getOrAssignResource(id1)
        resourceManager.getId(r1 + 10) shouldBe None
      }
    }

    "getting the resource for an id" must {
      "return the correct resource if the id is registered" in {
        val resourceManager = new ResourceManager[String]()
        val r1 = resourceManager.getOrAssignResource(id1)
        resourceManager.getResource(id1) shouldBe Some(r1)
      }

      "return None if the id is not registered" in {
        val resourceManager = new ResourceManager[String]()
        resourceManager.getOrAssignResource(id1)
        resourceManager.getResource(id2) shouldBe None
      }
    }

    "releasing a id by resource" must {
      "release the correct id" in {
        val resourceManager = new ResourceManager[String]()
        val r1 = resourceManager.getOrAssignResource(id1)
        val r2 = resourceManager.getOrAssignResource(id2)

        resourceManager.hasResource(r1) shouldBe true
        resourceManager.hasId(id1) shouldBe true
        resourceManager.hasResource(r2) shouldBe true
        resourceManager.hasId(id2) shouldBe true

        resourceManager.releaseResource(r1)

        resourceManager.hasResource(r1) shouldBe false
        resourceManager.hasId(id1) shouldBe false
        resourceManager.hasResource(r2) shouldBe true
        resourceManager.hasId(id2) shouldBe true
      }
    }

    "releasing a resource by id" must {
      "release the correct resource" in {
        val resourceManager = new ResourceManager[String]()
        val r1 = resourceManager.getOrAssignResource(id1)
        val r2 = resourceManager.getOrAssignResource(id2)

        resourceManager.hasResource(r1) shouldBe true
        resourceManager.hasId(id1) shouldBe true
        resourceManager.hasResource(r2) shouldBe true
        resourceManager.hasId(id2) shouldBe true

        resourceManager.releaseResourceForId(id1)

        resourceManager.hasResource(r1) shouldBe false
        resourceManager.hasId(id1) shouldBe false
        resourceManager.hasResource(r2) shouldBe true
        resourceManager.hasId(id2) shouldBe true
      }
    }
  }
}
