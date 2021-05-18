/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.server.backend.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.schema.NonRecordingSchemaManager
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.collection.CollectionPermissions
import com.convergencelabs.convergence.server.model.domain.model.{ModelPermissions, ObjectValue}
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class CollectionPermissionsStoreSpec
  extends PersistenceStoreSpec[DomainPersistenceProvider](NonRecordingSchemaManager.SchemaType.Domain)
    with AnyWordSpecLike
    with Matchers {

  private val modelPermissions = ModelPermissions(read = true, write = true, remove = true, manage = true)

  private val collection1 = "collection1"
  private val model1 = "model1"
  private val model2 = "model2"
  private val user1 = DomainUserId.normal("user1")
  private val user2 = DomainUserId.normal("user2")
  private val nonExistentCollectionId = "not_real"
  private val nonRealId = "not_real"

  def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider = new DomainPersistenceProviderImpl(DomainId("ns", "domain"), dbProvider)

  "A CollectionPermissionsStore" when {
    "retrieving the collection world permissions" must {
      "be equal to those just set" in withTestData { provider =>
        val permissions = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionWorldPermissions(collection1, permissions).get
        val retrievedPermissions = provider.collectionPermissionsStore.getCollectionWorldPermissions(collection1).get
        retrievedPermissions shouldEqual permissions
      }

      "fail if collection does not exist" in withTestData { provider =>
        provider.collectionPermissionsStore
          .getCollectionWorldPermissions(nonExistentCollectionId)
          .failed.get shouldBe an[EntityNotFoundException]
      }
    }

    "retrieving the collection user permissions" must {
      "be equal to those just set" in withTestData { provider =>
        val permissions = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.updateCollectionUserPermissions(collection1, user1, permissions).get
        val retrievedPermissions = provider.collectionPermissionsStore.getCollectionPermissionsForUser(collection1, user1).get
        retrievedPermissions shouldEqual Some(permissions)
      }

      "be none if no permissions are set" in withTestData { provider =>
        val retrievedPermissions = provider.collectionPermissionsStore.getCollectionPermissionsForUser(collection1, user1).get
        retrievedPermissions shouldEqual None
      }
    }

    "retrieving all collection user permissions" must {
      "contain all those just set" in withTestData { provider =>
        val permissions = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.updateCollectionUserPermissions(collection1, user1, permissions).get
        provider.collectionPermissionsStore.updateCollectionUserPermissions(collection1, user2, permissions).get
        val retrievedPermissions = provider.collectionPermissionsStore.getAllCollectionUserPermissions(collection1).get
        retrievedPermissions shouldEqual Map(user1 -> permissions, user2 -> permissions)
      }

      "fail if collection does not exist" in withTestData { provider =>
        an[EntityNotFoundException] should be thrownBy provider.collectionPermissionsStore.getAllCollectionUserPermissions(nonExistentCollectionId).get
      }
    }

    "deleting a collection user permissions" must {
      "must no longer be set on the collection" in withTestData { provider =>
        val permissions = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.updateCollectionUserPermissions(collection1, user1, permissions).get
        provider.collectionPermissionsStore.updateCollectionUserPermissions(collection1, user2, permissions).get
        provider.collectionPermissionsStore.removeCollectionUserPermissions(collection1, user1)
        val retrievedPermissions = provider.collectionPermissionsStore.getAllCollectionUserPermissions(collection1).get
        retrievedPermissions shouldEqual Map(user2 -> permissions)
      }
    }

    "removing all permissions for a user" must {
      "delete the correct permissions for models and collections" in withTestData { provider =>
        val cp1 = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.updateCollectionUserPermissions(collection1, user1, cp1).get

        val cp2 = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.updateCollectionUserPermissions(collection1, user2, cp2).get

        provider.collectionPermissionsStore.removeAllCollectionPermissionsForUser(user1)

        val retrievedCps = provider.collectionPermissionsStore.getAllCollectionUserPermissions(collection1).get
        retrievedCps shouldEqual Map(user2 -> cp2)

        val retrievedCp = provider.collectionPermissionsStore.getCollectionPermissionsForUser(collection1, user1).get
        retrievedCp shouldEqual None
      }
    }
  }

  def withTestData(testCode: DomainPersistenceProvider => Any): Unit = {
    this.withPersistenceStore { provider =>
      provider.collectionStore.ensureCollectionExists(collection1).get
      provider.modelStore.createModel(model1, collection1, ObjectValue("vid", Map()), None, overridePermissions = true, modelPermissions).get
      provider.modelStore.createModel(model2, collection1, ObjectValue("vid", Map()), None, overridePermissions = true, modelPermissions).get
      provider.userStore.createDomainUser(DomainUser(user1.userType, user1.username, None, None, None, None, None)).get
      provider.userStore.createDomainUser(DomainUser(user2.userType, user2.username, None, None, None, None, None)).get
      testCode(provider)
    }
  }
}
