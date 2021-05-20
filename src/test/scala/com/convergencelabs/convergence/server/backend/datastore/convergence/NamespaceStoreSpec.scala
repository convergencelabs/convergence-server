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

package com.convergencelabs.convergence.server.backend.datastore.convergence

import com.convergencelabs.convergence.server.backend.datastore.convergence.NamespaceStore.NamespaceUpdates
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException, PersistenceStoreSpec}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.schema.NonRecordingSchemaManager
import com.convergencelabs.convergence.server.model.server.domain.Namespace
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object NamespaceStoreSpec {
  case class SpecStores(namespace: NamespaceStore)
}

class NamespaceStoreSpec
  extends PersistenceStoreSpec[NamespaceStoreSpec.SpecStores](NonRecordingSchemaManager.SchemaType.Convergence)
  with AnyWordSpecLike
  with Matchers {

  def createStore(dbProvider: DatabaseProvider): NamespaceStoreSpec.SpecStores = {
    NamespaceStoreSpec.SpecStores(new NamespaceStore(dbProvider))
  }

  private val Namespace1 = Namespace("namespace1", "Namespace 1", userNamespace = false)
  private val Namespace2 = Namespace("namespace2", "Namespace 2", userNamespace = false)

  "A NamespaceStore" when {

    "asked whether a namespace exists" must {
      "return false if it doesn't exist" in withPersistenceStore { stores =>
        stores.namespace.namespaceExists(Namespace1.id).get shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.namespaceExists(Namespace1.id).get shouldBe true
      }
    }

    "retrieving a namespace by id" must {
      "return None if the namespace doesn't exist" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.getNamespace("none").get shouldBe None
      }

      "return the correct namespace if it exists" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.getNamespace(Namespace1.id).get shouldBe Some(Namespace1)
      }
    }

    "creating a namespace" must {
      "create the namespace correct record in the database" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.getNamespace(Namespace1.id).get shouldBe Some(Namespace1)
      }

      "allow creating multiple namespaces with non-conclifcting id's" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.createNamespace(Namespace2).get
        stores.namespace.getNamespace(Namespace1.id).get shouldBe Some(Namespace1)
        stores.namespace.getNamespace(Namespace2.id).get shouldBe Some(Namespace2)
      }

      "return a DuplicateValueExcpetion if a namesspace exists with the same id" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.createNamespace(Namespace(Namespace1.id, "other display name", userNamespace = false)).failure.exception shouldBe a[DuplicateValueException]
      }

      "return a DuplicateValueExcpetion if a namesspace exists with the same display name" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.createNamespace(Namespace("Other Id", Namespace1.displayName, userNamespace = false)).failure.exception shouldBe a[DuplicateValueException]
      }
    }

    "removing a namespace" must {
      "remove the domain record in the database if it exists" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.createNamespace(Namespace2).get
        stores.namespace.deleteNamespace(Namespace1.id).get
        stores.namespace.namespaceExists(Namespace1.id).get shouldBe false
        stores.namespace.namespaceExists(Namespace2.id).get shouldBe true
      }

      //      "not throw an exception if the domain does not exist" in withPersistenceStore { stores =>
      //        stores.namespace.deleteNamespace("none").failure.exception shouldBe a[EntityNotFoundException]
      //      }
    }

    "updating a namespace" must {
      "successfully update an existing namespace" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.createNamespace(Namespace2).get

        val updated = Namespace1.copy(displayName = "updated")
        stores.namespace.updateNamespace(NamespaceUpdates(updated.id, updated.displayName)).get

        stores.namespace.getNamespace(Namespace1.id).get shouldBe Some(updated)
        stores.namespace.getNamespace(Namespace2.id).get shouldBe Some(Namespace2)
      }

      "fail to update an non-existing namespace" in withPersistenceStore { stores =>
        val updated = Namespace1.copy(displayName = "updated")
        stores.namespace.updateNamespace(NamespaceUpdates(updated.id, updated.displayName)).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
  }
}
