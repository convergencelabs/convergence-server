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

package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.server.backend.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.model.domain.model.{ModelPermissions, ObjectValue}
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ModelPermissionsStoreSpec
  extends DomainPersistenceStoreSpec
    with AnyWordSpecLike
    with Matchers {

  private val modelPermissions = ModelPermissions(read = true, write = true, remove = true, manage = true)

  private val collection1 = "collection1"
  private val model1 = "model1"
  private val model2 = "model2"
  private val user1 = DomainUserId.normal("user1")
  private val user2 = DomainUserId.normal("user2")
  private val nonRealId = "not_real"

  "A ModelPermissionsStore" when {
    "retrieving the model world permissions" must {
      "be equal to those just set" in withTestData { provider =>
        val permissions = ModelPermissions(read = true, write = false, remove = true, manage = false)
        provider.modelPermissionsStore.setModelWorldPermissions(model1, permissions).get
        val retrievedPermissions = provider.modelPermissionsStore.getModelWorldPermissions(model1).get
        retrievedPermissions shouldEqual permissions
      }

      "fail if model does not exist" in withTestData { provider =>
        an[EntityNotFoundException] should be thrownBy provider.modelPermissionsStore.getModelWorldPermissions(nonRealId).get
      }
    }

    "retrieving the model user permissions" must {
      "be equal to those just set" in withTestData { provider =>
        val permissions = ModelPermissions(read = true, write = false, remove = true, manage = false)
        provider.modelPermissionsStore.updateModelUserPermissions(model1, user1, permissions).get
        val retrievedPermissions = provider.modelPermissionsStore.getModelUserPermissions(model1, user1).get
        retrievedPermissions shouldEqual Some(permissions)
      }

      "be none if no permissions are set" in withTestData { provider =>
        val retrievedPermissions = provider.modelPermissionsStore.getModelUserPermissions(model1, user1).get
        retrievedPermissions shouldEqual None
      }
    }

    "retrieving all model user permissions" must {
      "contain all those just set" in withTestData { provider =>
        val permissions = ModelPermissions(read = true, write = false, remove = true, manage = false)
        provider.modelPermissionsStore.updateModelUserPermissions(model1, user1, permissions).get
        provider.modelPermissionsStore.updateModelUserPermissions(model1, user2, permissions).get
        val retrievedPermissions = provider.modelPermissionsStore.getAllModelUserPermissions(model1).get
        retrievedPermissions shouldEqual Map(user1 -> permissions, user2 -> permissions)
      }

      "fail if model does not exist" in withTestData { provider =>
        an[EntityNotFoundException] should be thrownBy provider.modelPermissionsStore.getAllModelUserPermissions(nonRealId).get
      }

      "contain all those just set via update all" in withTestData { provider =>
        val permissions = ModelPermissions(read = true, write = false, remove = true, manage = false)
        provider.modelPermissionsStore.updateAllModelUserPermissions(model1, Map(user1 -> Some(permissions))).get
        provider.modelPermissionsStore.updateAllModelUserPermissions(model1, Map(user2 -> Some(permissions))).get
        val retrievedPermissions = provider.modelPermissionsStore.getAllModelUserPermissions(model1).get
        retrievedPermissions shouldEqual Map(user1 -> permissions, user2 -> permissions)
      }
    }

    "deleting a model user permissions" must {
      "must no longer be set on the model" in withTestData { provider =>
        val permissions = ModelPermissions(read = true, write = false, remove = true, manage = false)
        provider.modelPermissionsStore.updateModelUserPermissions(model1, user1, permissions).get
        provider.modelPermissionsStore.updateModelUserPermissions(model1, user2, permissions).get
        provider.modelPermissionsStore.removeModelUserPermissions(model1, user1)
        val retrievedPermissions = provider.modelPermissionsStore.getAllModelUserPermissions(model1).get
        retrievedPermissions shouldEqual Map(user2 -> permissions)
      }
    }

    "removing all permissions for a user" must {
      "delete the correct permissions for models" in withTestData { provider =>

        val mp1 = ModelPermissions(read = true, write = false, remove = true, manage = false)
        provider.modelPermissionsStore.updateModelUserPermissions(model1, user1, mp1).get

        val mp2 = ModelPermissions(read = true, write = false, remove = true, manage = false)
        provider.modelPermissionsStore.updateModelUserPermissions(model1, user2, mp2).get

        provider.modelPermissionsStore.removeAllModelPermissionsForUser(user1)

        val retrievedMps = provider.modelPermissionsStore.getAllModelUserPermissions(model1).get
        retrievedMps shouldEqual Map(user2 -> mp2)

        val retrievedMp = provider.modelPermissionsStore.getModelUserPermissions(model1, user1).get
        retrievedMp shouldEqual None
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
