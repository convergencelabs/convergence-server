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

import com.convergencelabs.convergence.server.backend.datastore.domain.group.UserGroupStore
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserStore
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.schema.DeltaCategory
import com.convergencelabs.convergence.server.model.domain.group.UserGroup
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class UserGroupStoreSpec
    extends PersistenceStoreSpec[(UserGroupStore, DomainUserStore)](DeltaCategory.Domain)
    with AnyWordSpecLike
    with Matchers {

  private val User1 = DomainUser(DomainUserType.Normal, "test1", Some("Test"), Some("One"), Some("Test One"), Some("test1@example.com"), None)
  private val User2 = DomainUser(DomainUserType.Normal, "test2", Some("Test"), Some("Two"), Some("Test Two"), Some("test2@example.com"), None)
  private val User3 = DomainUser(DomainUserType.Normal, "test3", Some("Test"), Some("Three"), Some("Test Two"), Some("test3@example.com"), None)

  private val group1 = UserGroup("id1", "group 1", Set(User1.toUserId, User2.toUserId))
  private val duplicateGroup = UserGroup(group1.id, "duplicate id", Set(User1.toUserId, User2.toUserId))
  private val group2 = UserGroup("id2", "group 2", Set(User2.toUserId))
  private val group3 = UserGroup("id3", "group 3", Set())

  private val noUserId = DomainUserId.normal("no-user")

  def createStore(dbProvider: DatabaseProvider): (UserGroupStore, DomainUserStore) = (new UserGroupStore(dbProvider), new DomainUserStore(dbProvider))

  "A UserGroupStore" when {
    "creating a user group" must {
      "be able to get the user group that was created" in withUsers { store =>
        store.createUserGroup(group1).get
        val queried = store.getUserGroup(group1.id).get.value
        queried shouldBe group1
      }

      "disallow duplicate ids" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(duplicateGroup).failure.exception shouldBe a[DuplicateValueException]
      }

      "disallow creating a group with an unknown user" in withUsers { store =>
        store.createUserGroup(UserGroup("id1", "group 1", Set(User1.toUserId, noUserId))).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
    
    "getting a UserGroup" must {
      "should return none for a group that doesn't exists" in withUsers { store =>
        store.getUserGroup("no group").get shouldBe None
      }
    }

    "deleted a user group" must {
      "delete an existing group" in withUsers { store =>
        store.createUserGroup(group1).get
        store.getUserGroup(group1.id).get.value shouldBe group1
        store.deleteUserGroup(group1.id).get
        store.getUserGroup(group1.id).get shouldBe None
      }
      
      "fail with EntityNotFound for a group that does not exists" in withUsers { store =>
        store.deleteUserGroup("foo").failure.exception shouldBe a[EntityNotFoundException]
      }
    }

    "updating a user group" must {
      "correctly update only the specified id" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        val updated = group1.copy(description = "test")
        store.updateUserGroup(group1.id, updated).get
        val updatedRead = store.getUserGroup(group1.id).get.value
        updatedRead shouldBe updated

        val group2dRead = store.getUserGroup(group2.id).get.value
        group2dRead shouldBe group2
      }

      "correctly update with a new id" in withUsers { store =>
        store.createUserGroup(group1).get
        val updated = group1.copy(id = "test")
        
        store.updateUserGroup(group1.id, updated).get

        val updatedRead = store.getUserGroup(updated.id).get.value
        updatedRead shouldBe updated

        store.getUserGroup(group1.id).get shouldBe None
      }

      "not allow changing the id to an existing id" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        val updated = group1.copy(id = group2.id)
        store.updateUserGroup(group1.id, updated).failure.exception shouldBe a[DuplicateValueException]
      }
    }
    
    "adding a user" must {
      "add a user that is not already a member" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        
        val expected = group1.copy(members = group1.members + User3.toUserId )
        store.addUserToGroup(group1.id, User3.toUserId).get
        val updatedRead = store.getUserGroup(group1.id).get.value
        updatedRead shouldBe expected

        // make sure we didn't add to anyone other group.
        store.getUserGroup(group2.id).get.value shouldBe group2
      }
      
      "ingore adding a user that is not already" in withUsers { store =>
        store.createUserGroup(group1).get
        
        store.addUserToGroup(group1.id, User1.toUserId)
        val updatedRead = store.getUserGroup(group1.id).get.value
        updatedRead shouldBe group1
      }
      
      "fail with EntityNotFound for a user that does not exist" in withUsers { store =>
        store.createUserGroup(group1).get
        store.addUserToGroup(group1.id, noUserId).failure.exception shouldBe a[EntityNotFoundException]
      }
      
      "fail with EntityNotFound for a group that does not exist" in withUsers { store =>
        store.addUserToGroup("no group", User1.toUserId).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
    
    "removing a user" must {
      "remove a user that is not already a member" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        
        val expected = group1.copy(members = group1.members - User2.toUserId )
        store.removeUserFromGroup(group1.id, User2.toUserId)
        val updatedRead = store.getUserGroup(group1.id).get.value
        updatedRead shouldBe expected

        // make sure we didn't add to anyone other group.
        store.getUserGroup(group2.id).get.value shouldBe group2
      }
      
      "ingore removing a user that is not already a memeber" in withUsers { store =>
        store.createUserGroup(group1).get
        
        store.removeUserFromGroup(group1.id, noUserId)
        val updatedRead = store.getUserGroup(group1.id).get.value
        updatedRead shouldBe group1
      }
      
      "fail with EntityNotFound for a user that does not exist" in withUsers { store =>
        store.createUserGroup(group1).get
        store.removeUserFromGroup(group1.id, noUserId).failure.exception shouldBe a[EntityNotFoundException]
      }
      
      "fail with EntityNotFound for a group that does not exist" in withUsers { store =>
        store.removeUserFromGroup("no group", User1.toUserId).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
    
    "getting groups by ids" must {
      "return correct groups with valid ids" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        
        store.getUserGroupsById(List(group1.id, group2.id)).get shouldBe List(group1, group2)
      }
      
      "fail with EntityNotFound if a non-existent group id is in the list" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        store.getUserGroupsById(List(group1.id, "no group", group2.id)).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
    
    "getting group ids for a user" must {
      "return correct groups with valid ids" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        
        store.getUserGroupIdsForUser(User1.toUserId).get shouldBe Set(group1.id)
        store.getUserGroupIdsForUser(User2.toUserId).get shouldBe Set(group1.id, group2.id)
        store.getUserGroupIdsForUser(User3.toUserId).get shouldBe Set()
      }
      
      "fail with EntityNotFound if a non-existant group id is in the list" in withUsers { store =>
        store.getUserGroupIdsForUser(noUserId).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
    
    "getting group ids for a users" must {
      "return correct groups with valid ids" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        
        val map = store.getUserGroupIdsForUsers(List(User1.toUserId, User2.toUserId, User3.toUserId)).get
        map.size shouldBe 3
        map(User1.toUserId) shouldBe Set(group1.id)
        map(User2.toUserId) shouldBe Set(group1.id, group2.id)
        map(User3.toUserId) shouldBe Set()
      }
      
      "fail with EntityNotFound if a non-existant group id is in the list" in withUsers { store =>
        store.getUserGroupIdsForUsers(List(User1.toUserId, noUserId)).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
    
    "setting groups for a user" must {
      "correctly set groups for a user with no existing groups" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        store.setGroupsForUser(User3.toUserId, Set(group1.id, group2.id)).get
        store.getUserGroupIdsForUser(User3.toUserId).get shouldBe Set(group1.id, group2.id)
      }
      
      "correctly set groups for a user with existing groups" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        store.createUserGroup(group3).get
        store.setGroupsForUser(User1.toUserId, Set(group3.id)).get
        store.getUserGroupIdsForUser(User1.toUserId).get shouldBe Set(group3.id)
      }
    }
  }

  def withUsers(testCode: UserGroupStore => Any): Unit = {
    super.withPersistenceStore { stores =>
      val userStore = stores._2
      userStore.createDomainUser(User1).get
      userStore.createDomainUser(User2).get
      userStore.createDomainUser(User3).get
      testCode(stores._1)
    }
  }
}