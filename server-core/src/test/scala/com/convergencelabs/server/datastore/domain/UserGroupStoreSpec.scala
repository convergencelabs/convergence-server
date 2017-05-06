package com.convergencelabs.server.datastore.domain

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.datastore.EntityNotFoundException

class UserGroupStoreSpec
    extends PersistenceStoreSpec[(UserGroupStore, DomainUserStore)](DeltaCategory.Domain)
    with WordSpecLike
    with Matchers {
  
  val User1 = DomainUser(DomainUserType.Normal, "test1", Some("Test"), Some("One"), Some("Test One"), Some("test1@example.com"))
  val User2 = DomainUser(DomainUserType.Normal, "test2", Some("Test"), Some("Two"), Some("Test Two"), Some("test2@example.com"))
  val User3 = DomainUser(DomainUserType.Normal, "test3", Some("Test"), Some("Three"), Some("Test Two"), Some("test3@example.com"))


  val newGroup = UserGroup("id1", "group 1", Set(User1.username, User2.username))
  val duplicateGroup = UserGroup("id1", "duplicate id",  Set("test1", "test2"))
  val group2 = UserGroup("id2", "group 2", Set(User2.username, User3.username))

  def createStore(dbProvider: DatabaseProvider): (UserGroupStore, DomainUserStore)
  = (new UserGroupStore(dbProvider), new DomainUserStore(dbProvider))

  "A UserGroupStore" when {
    "creating a user group" must {
      "be able to get the user group that was created" in withUsers { store =>
        store.createUserGroup(newGroup).get
        val queried = store.getGroup(newGroup.id).get.value
        queried shouldBe newGroup
      }

      "disallow duplicate ids" in withUsers { store =>
        store.createUserGroup(newGroup).get
        store.createUserGroup(duplicateGroup).failure.exception shouldBe a[DuplicateValueException]
      }
      
      "disallow creating a group with an unknown user" in withUsers { store =>
        store.createUserGroup(UserGroup("id1", "group 1", Set("test1", "unknown"))).failure.exception shouldBe a[EntityNotFoundException]
      }
    }

    "updating a user group" must {
      "correctly update only the specified id" in withUsers { store =>
        store.createUserGroup(newGroup).get
        store.createUserGroup(group2).get
        val updated = newGroup.copy(description = "test")
        store.updateUserGroup(newGroup.id, updated).get
        val updatedRead = store.getGroup(newGroup.id).get.value
        updatedRead shouldBe updated
        
        val group2dRead = store.getGroup(group2.id).get.value
        group2dRead shouldBe group2
      }
      
      "correctly update with a new id" in withUsers { store =>
//        store.createUserGroup(newGroup).get
//        val updated = newGroup.copy(id = "test")
//        store.updateUserGroup(newGroup.id, updated)
//        
//        store.getGroup(newGroup.id).get shouldBe None
//        val updatedRead = store.getGroup(updated.id).get.value
//        updatedRead shouldBe updated
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