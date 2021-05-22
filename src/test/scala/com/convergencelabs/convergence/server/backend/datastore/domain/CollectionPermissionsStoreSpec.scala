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
import com.convergencelabs.convergence.server.model.domain.collection.CollectionPermissions
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class CollectionPermissionsStoreSpec
  extends DomainPersistenceStoreSpec
    with AnyWordSpecLike
    with Matchers {

  private val collection1 = "collection1"
  private val collection2 = "collection2"
  private val nonExistentCollectionId = "not_real"

  private val user1 = DomainUserId.normal("user1")
  private val user2 = DomainUserId.normal("user2")

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
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection1, user1, permissions).get
        val retrievedPermissions = provider.collectionPermissionsStore.getCollectionPermissionsForUser(collection1, user1).get
        retrievedPermissions shouldEqual Some(permissions)
      }

      "be none if no permissions are set" in withTestData { provider =>
        val retrievedPermissions = provider.collectionPermissionsStore.getCollectionPermissionsForUser(collection1, user1).get
        retrievedPermissions shouldEqual None
      }
    }

    "setting all collection user permissions for a collection" must {
      "correctly set permissions for that collection" in withTestData { provider =>
        val c1u1 = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        val c1u2 = CollectionPermissions(create = true, read = true, write = false, remove = true, manage = false)

        val permissionsByUser = Map(user1 -> c1u1, user2 -> c1u2)
        provider.collectionPermissionsStore
          .setUserPermissionsForCollection(collection1, permissionsByUser)

        provider.collectionPermissionsStore
          .getUserPermissionsForCollection(collection1).get shouldBe permissionsByUser
      }

      "remove existing permissions" in withTestData { provider =>
        val c1u1 = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        val c1u2 = CollectionPermissions(create = true, read = true, write = false, remove = true, manage = false)

        val originalPermissions = Map(user1 -> c1u1, user2 -> c1u2)
        provider.collectionPermissionsStore
          .setUserPermissionsForCollection(collection1, originalPermissions)

        val newPermissions = Map(user1 -> c1u1)
        provider.collectionPermissionsStore
          .setUserPermissionsForCollection(collection1, newPermissions)

        provider.collectionPermissionsStore
          .getUserPermissionsForCollection(collection1).get shouldBe newPermissions
      }

      "not change permissions for another collection" in withTestData { provider =>
        val c1u1 = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        val c1u2 = CollectionPermissions(create = true, read = true, write = false, remove = true, manage = false)

        val collection1Permissions = Map(user1 -> c1u1, user2 -> c1u2)
        provider.collectionPermissionsStore
          .setUserPermissionsForCollection(collection1, collection1Permissions)

        val c2u1 = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = true)

        val collection2Permissions = Map(user1 -> c2u1)
        provider.collectionPermissionsStore
          .setUserPermissionsForCollection(collection2, collection2Permissions)

        provider.collectionPermissionsStore
          .getUserPermissionsForCollection(collection1).get shouldBe collection1Permissions
      }
    }

    "retrieving all collection user permissions for a collection" must {
      "contain all those just set" in withTestData { provider =>
        val permissions = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection1, user1, permissions).get
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection1, user2, permissions).get
        val retrievedPermissions = provider.collectionPermissionsStore.getUserPermissionsForCollection(collection1).get
        retrievedPermissions shouldEqual Map(user1 -> permissions, user2 -> permissions)
      }

      "fail if collection does not exist" in withTestData { provider =>
        an[EntityNotFoundException] should be thrownBy provider.collectionPermissionsStore.getUserPermissionsForCollection(nonExistentCollectionId).get
      }
    }

    "retrieving all collection user permissions for multiple collections" must {
      "contain all permissions for the specified collections" in withTestData { provider =>
        val c1u1 = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection1, user1, c1u1).get

        val c1u2 = CollectionPermissions(create = true, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection1, user2, c1u2).get

        val c2u1 = CollectionPermissions(create = true, read = true, write = true, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection2, user1, c2u1).get

        provider.collectionPermissionsStore
          .getUserPermissionsForCollections(List(collection1, collection2))
          .get shouldEqual
          Map(
            collection1 -> Map(user1 -> c1u1, user2 -> c1u2),
            collection2 -> Map(user1 -> c2u1),
          )
      }

      "contain no entry for a collection with no permissions" in withTestData { provider =>
        provider.collectionPermissionsStore
          .getUserPermissionsForCollections(List(collection1))
          .get shouldEqual
          Map()
      }
    }

    "getting collection permissions for a user" must {
      "get the permissions if the user has them" in withTestData { provider =>
        val permissions = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore
          .setCollectionPermissionsForUser(collection1, user1, permissions).get
        provider.collectionPermissionsStore
          .getCollectionPermissionsForUser(collection1, user1).get shouldBe Some(permissions)
      }

      "return None if the user does not have permissions" in withTestData { provider =>
        provider.collectionPermissionsStore
          .getCollectionPermissionsForUser(collection1, user1).get shouldBe None
      }
    }

    "removing collection permissions for a user" must {
      "remove the users permissions from the collection" in withTestData { provider =>
        val permissions = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection1, user1, permissions).get
        provider.collectionPermissionsStore.removeCollectionPermissionsForUser(collection1, user1)

        val retrievedPermissions = provider.collectionPermissionsStore.getUserPermissionsForCollection(collection1).get
        retrievedPermissions shouldEqual Map()
      }

      "not remove other users permissions from the collection" in withTestData { provider =>
        val permissions = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection1, user2, permissions).get
        provider.collectionPermissionsStore.removeCollectionPermissionsForUser(collection1, user1)

        val retrievedPermissions = provider.collectionPermissionsStore.getUserPermissionsForCollection(collection1).get
        retrievedPermissions shouldEqual Map(user2 -> permissions)
      }

      "not remove the users permissions for another collection" in withTestData { provider =>
        val permissions = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection2, user1, permissions).get
        provider.collectionPermissionsStore.removeCollectionPermissionsForUser(collection1, user1)

        val retrievedPermissions = provider.collectionPermissionsStore.getUserPermissionsForCollection(collection2).get
        retrievedPermissions shouldEqual Map(user1 -> permissions)
      }
    }

    "removing all permissions for a user" must {
      "delete the correct permissions for collections" in withTestData { provider =>
        val cp1 = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection1, user1, cp1).get

        val cp2 = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection1, user2, cp2).get

        provider.collectionPermissionsStore.removeAllCollectionPermissionsForUser(user1)

        val retrievedCps = provider.collectionPermissionsStore.getUserPermissionsForCollection(collection1).get
        retrievedCps shouldEqual Map(user2 -> cp2)

        val retrievedCp = provider.collectionPermissionsStore.getCollectionPermissionsForUser(collection1, user1).get
        retrievedCp shouldEqual None
      }
    }

    "removing all permissions for a collection" must {
      "delete the all permissions for the collection" in withTestData { provider =>
        val cp1 = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection1, user1, cp1).get
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection2, user1, cp1).get

        val cp2 = CollectionPermissions(create = false, read = true, write = false, remove = true, manage = false)
        provider.collectionPermissionsStore.setCollectionPermissionsForUser(collection1, user2, cp2).get

        provider.collectionPermissionsStore.removeUserPermissionsForCollection(collection1)


        provider.collectionPermissionsStore.getUserPermissionsForCollection(collection1).get shouldEqual Map()
        provider.collectionPermissionsStore.getUserPermissionsForCollection(collection2).get shouldEqual
          Map(user1 -> cp1)
      }
    }
  }

  def withTestData(testCode: DomainPersistenceProvider => Any): Unit = {
    this.withPersistenceStore { provider =>
      provider.collectionStore.ensureCollectionExists(collection1).get
      provider.collectionStore.ensureCollectionExists(collection2).get
      provider.userStore.createDomainUser(DomainUser(user1.userType, user1.username, None, None, None, None, None)).get
      provider.userStore.createDomainUser(DomainUser(user2.userType, user2.username, None, None, None, None, None)).get
      testCode(provider)
    }
  }
}
