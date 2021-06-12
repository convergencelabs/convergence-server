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

package com.convergencelabs.convergence.server.util

import com.convergencelabs.convergence.server.model.DomainId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class DomainIdEntitySerializerSpec extends AnyWordSpecLike with Matchers {

  "An DomainIdEntitySerializer" when {
    "serializing a domain id" must {
      "serialize and deserialize simple domain id" in {
        val domainId = DomainId("convergence", "default")
        val serializer = new DomainIdEntityIdSerializer()
        val serialized = serializer.serialize(domainId)
        val deserialized = serializer.deserialize(serialized)
        deserialized shouldBe domainId
      }

      "serialize and deserialize a domain id with the separator in the namespace" in {
        val domainId = DomainId(s"convergence${EntityIdSerializer.Separator}default", "default")
        val serializer = new DomainIdEntityIdSerializer()
        val serialized = serializer.serialize(domainId)
        val deserialized = serializer.deserialize(serialized)
        deserialized shouldBe domainId
      }

      "serialize and deserialize a domain id with the separator in the domainId" in {
        val domainId = DomainId("convergence", s"${EntityIdSerializer.Separator}default")
        val serializer = new DomainIdEntityIdSerializer()
        val serialized = serializer.serialize(domainId)
        val deserialized = serializer.deserialize(serialized)
        deserialized shouldBe domainId
      }

      "serialize and deserialize a domain id whith a namespace and domain id that is the separator" in {
        val domainId = DomainId(EntityIdSerializer.Separator, EntityIdSerializer.Separator)
        val serializer = new DomainIdEntityIdSerializer()
        val serialized = serializer.serialize(domainId)
        val deserialized = serializer.deserialize(serialized)
        deserialized shouldBe domainId
      }
    }
  }
}
