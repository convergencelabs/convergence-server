package com.convergencelabs.server.datastore.domain

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType

class UserGroupStoreSpec
    extends PersistenceStoreSpec[(UserGroupStore, DomainUserStore)](DeltaCategory.Domain)
    with WordSpecLike
    with Matchers {

  val User1 = DomainUser(DomainUserType.Normal, "test1", Some("Test"), Some("One"), Some("Test One"), Some("test1@example.com"))
  val User2 = DomainUser(DomainUserType.Normal, "test2", Some("Test"), Some("Two"), Some("Test Two"), Some("test2@example.com"))
  val User3 = DomainUser(DomainUserType.Normal, "test3", Some("Test"), Some("Three"), Some("Test Two"), Some("test3@example.com"))

  val group1 = UserGroup("id1", "group 1", Set(User1.username, User2.username))
  val duplicateGroup = UserGroup(group1.id, "duplicate id", Set(User1.username, User2.username))
  val group2 = UserGroup("id2", "group 2", Set(User2.username))

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
        store.createUserGroup(UserGroup("id1", "group 1", Set("test1", "unknown"))).failure.exception shouldBe a[EntityNotFoundException]
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
        println(updated)
        store.updateUserGroup(group1.id, updated).get

        val updatedRead = store.getUserGroup(updated.id).get.value
        updatedRead shouldBe updated

        store.getUserGroup(group1.id).get shouldBe None
      }

      "not allow changing the id to an existin id" in withUsers { store =>
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
        
        val expected = group1.copy(members = (group1.members + User3.username) )
        store.addUserToGroup(group1.id, User3.username)
        val updatedRead = store.getUserGroup(group1.id).get.value
        updatedRead shouldBe expected

        // make sure we didn't add to anyone other group.
        store.getUserGroup(group2.id).get.value shouldBe group2
      }
      
      "ingore adding a user that is not already" in withUsers { store =>
        store.createUserGroup(group1).get
        
        store.addUserToGroup(group1.id, User1.username)
        val updatedRead = store.getUserGroup(group1.id).get.value
        updatedRead shouldBe group1
      }
      
      "fail with EntityNotFound for a user that does not exist" in withUsers { store =>
        store.createUserGroup(group1).get
        store.addUserToGroup(group1.id, "no one").failure.exception shouldBe a[EntityNotFoundException]
      }
      
      "fail with EntityNotFound for a group that does not exist" in withUsers { store =>
        store.addUserToGroup("no group", User1.username).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
    
    "removing a user" must {
      "remove a user that is not already a member" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        
        val expected = group1.copy(members = (group1.members - User2.username) )
        store.removeUserFromGroup(group1.id, User2.username)
        val updatedRead = store.getUserGroup(group1.id).get.value
        updatedRead shouldBe expected

        // make sure we didn't add to anyone other group.
        store.getUserGroup(group2.id).get.value shouldBe group2
      }
      
      "ingore removing a user that is not already" in withUsers { store =>
        store.createUserGroup(group1).get
        
        store.removeUserFromGroup(group1.id, "")
        val updatedRead = store.getUserGroup(group1.id).get.value
        updatedRead shouldBe group1
      }
      
      "fail with EntityNotFound for a user that does not exist" in withUsers { store =>
        store.createUserGroup(group1).get
        store.removeUserFromGroup(group1.id, "no one").failure.exception shouldBe a[EntityNotFoundException]
      }
      
      "fail with EntityNotFound for a group that does not exist" in withUsers { store =>
        store.removeUserFromGroup("no group", User1.username).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
    
    "getting groups by ids" must {
      "return correct groups with valid ids" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        
        store.getUserGroupsById(List(group1.id, group2.id)).get shouldBe List(group1, group2)
      }
      
      "fail with EntityNotFound if a non-existant group id is in the list" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        store.getUserGroupsById(List(group1.id, "no group", group2.id)).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
    
    "getting group ids for a user" must {
      "return correct groups with valid ids" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        
        store.getUserGroupIdsForUser(User1.username).get shouldBe List(group1.id)
        store.getUserGroupIdsForUser(User2.username).get.toSet shouldBe Set(group1.id, group2.id)
        store.getUserGroupIdsForUser(User3.username).get shouldBe List()
      }
      
      "fail with EntityNotFound if a non-existant group id is in the list" in withUsers { store =>
        store.getUserGroupIdsForUser("no user").failure.exception shouldBe a[EntityNotFoundException]
      }
    }
    
    "getting group ids for a users" must {
      "return correct groups with valid ids" in withUsers { store =>
        store.createUserGroup(group1).get
        store.createUserGroup(group2).get
        
        val map = store.getUserGroupIdsForUsers(List(User1.username, User2.username, User3.username)).get
        map.size shouldBe 3
        map.get(User1.username).get shouldBe List(group1.id)
        map.get(User2.username).get.toSet shouldBe Set(group1.id, group2.id)
        map.get(User3.username).get shouldBe List()
      }
      
      "fail with EntityNotFound if a non-existant group id is in the list" in withUsers { store =>
        store.getUserGroupIdsForUsers(List(User1.username, "no user")).failure.exception shouldBe a[EntityNotFoundException]
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