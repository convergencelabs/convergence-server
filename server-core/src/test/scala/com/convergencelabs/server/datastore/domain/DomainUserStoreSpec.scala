package com.convergencelabs.server.datastore.domain

import java.time.Instant

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.SortOrder
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateNormalDomainUser
import com.convergencelabs.server.datastore.domain.DomainUserStore.UpdateDomainUser
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.InvalidValueExcpetion
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.domain.DomainUserId

class DomainUserStoreSpec
    extends PersistenceStoreSpec[DomainUserStore](DeltaCategory.Domain)
    with WordSpecLike
    with Matchers {

  // Pre-loaded Users
  val User0 = DomainUser(DomainUserType.Convergence, "admin", Some("Admin"), Some("User"), Some("Admin User"), Some("admin@example.com"))
  val User1 = DomainUser(DomainUserType.Normal, "test1", Some("Test"), Some("One"), Some("Test One"), Some("test1@example.com"))
  val User2 = DomainUser(DomainUserType.Normal, "test2", Some("Test"), Some("Two"), Some("Test Two"), Some("test2@example.com"))

  // New Users
  val User10 = CreateNormalDomainUser("user10", Some("first10"), Some("last10"), Some("first10 last10"), Some("user10@example.com"))
  val User11 = CreateNormalDomainUser("user11", Some("first11"), Some("last11"), Some("first11 last11"), Some("user11@example.com"))
  val User12 = CreateNormalDomainUser("user12", None, None, None, None)

  def createStore(dbProvider: DatabaseProvider): DomainUserStore = new DomainUserStore(dbProvider)

  "A DomainUserStore" when {
    "creating a user" must {
      "be able to get the user that was created" in withPersistenceStore { store =>
        store.createNormalDomainUser(User10).success

        val queried = store.getNormalDomainUser(User10.username)
        val DomainUser(userType, username, fname, lname, displayName, email, disabeld, deleted, deletedUsername) = queried.success.get.value
        CreateNormalDomainUser(username, fname, lname, displayName, email) shouldBe User10
      }

      "not allow duplicate usernames" in withPersistenceStore { store =>
        store.createNormalDomainUser(User10).success

        val duplicate = CreateNormalDomainUser(User10.username, User11.firstName, User11.lastName, User11.displayName, User11.email)
        store.createNormalDomainUser(duplicate).failure.exception shouldBe a[DuplicateValueException]
      }

      "allow creation of users with only username" in withPersistenceStore { store =>
        store.createNormalDomainUser(User12).get
        val queried = store.getNormalDomainUser(User12.username)
        val DomainUser(userType, username, fname, lname, displayName, email, disabled, deleted, deletedUsername) = queried.success.get.value
        CreateNormalDomainUser(username, fname, lname, displayName, email) shouldBe User12
      }
    }

    "removing a user" must {
      "correctly remove the user by username" in withPersistenceStore { store =>
        initUsers(store)
        store.deleteNormalDomainUser(User1.username).get
        val queried = store.getNormalDomainUser(User1.username)
        queried.success.get shouldBe None
      }

      "not throw exception if user does not exist" in withPersistenceStore { store =>
        store.deleteNormalDomainUser("DoesNotExit").failure.exception shouldBe a[EntityNotFoundException]
      }
    }

    "querying a user" must {

      "correctly retreive user by username" in withPersistenceStore { store =>
        initUsers(store)
        val queried = store.getNormalDomainUser(User1.username)
        queried.success.get.value shouldBe User1
      }
    }

    "querying multiple users" must {
      "correctly retreive users by username" in withPersistenceStore { store =>
        initUsers(store)
        val queried = store.getNormalDomainUsers(List(User1.username, User2.username))
        queried.get should contain allOf (User1, User2)
      }
    }

    "updating user" must {
      "throw exception if user does not exist" in withPersistenceStore { store =>
        store.updateDomainUser(UpdateDomainUser("foo", None, None, None, None, None)).failure.exception shouldBe a[EntityNotFoundException]
      }

      "currectly update an existing user, if unique properties are not violated" in withPersistenceStore { store =>
        initUsers(store)
        val update = UpdateDomainUser(User1.username, Some("f"), Some("l"), Some("d"), Some("e"), Some(true))
        val updated = DomainUser(DomainUserType.Normal, User1.username, Some("f"), Some("l"), Some("d"), Some("e"), true, false, None)
        store.updateDomainUser(update).get
        val queried = store.getNormalDomainUser(User1.username).get.get
        queried shouldBe updated
      }
    }

    "retreiving all users" must {
      "order correctly by username" in withPersistenceStore { store =>
        initUsers(store)
        val allUsers = store.getAllDomainUsers(None, None, None, None).get
        val orderedDescending = store.getAllDomainUsers(Some(DomainUserField.Username), Some(SortOrder.Descending), None, None).success.get
        val orderedAscending = store.getAllDomainUsers(Some(DomainUserField.Username), Some(SortOrder.Ascending), None, None).success.get

        orderedDescending shouldBe allUsers.sortWith(_.username > _.username)
        orderedAscending shouldBe orderedDescending.reverse
      }

      "limit results to the correct number" in withPersistenceStore { store =>
        initUsers(store)
        val allUser = store.getAllDomainUsers(None, None, None, None).get
        store.getAllDomainUsers(None, None, Some(2), None).success.get shouldBe allUser.slice(0, 2)
      }
    }

    "searching for users by fields" must {
      "return multiple matches if a prefix is supplied" in withPersistenceStore { store =>
        initUsers(store)
        val fields = List(DomainUserField.Username)
        val searchString = "test"
        val users = store.searchUsersByFields(fields, searchString, None, None, None, None).get
        users.length shouldBe 2
      }

      "return a single user if only one user matches" in withPersistenceStore { store =>
        initUsers(store)
        val fields = List(DomainUserField.Username)
        val searchString = "test1"
        val users = store.searchUsersByFields(fields, searchString, None, None, None, None).get
        users.length shouldBe 1
        users(0) shouldBe User1
      }
    }

    "checking whether a user exists" must {
      "return true if the user exist" in withPersistenceStore { store =>
        initUsers(store)
        store.domainUserExists(User1.username).success.get shouldBe true
      }

      "return false if the user does not exist" in withPersistenceStore { store =>
        store.domainUserExists("DoesNotExist").success.get shouldBe false
      }
    }

    "setting a users password" must {
      "correctly set the passwords from plaintext" in withPersistenceStore { store =>
        initUsers(store)
        
        store.setDomainUserPassword(User1.username, "password1").get // already set
        store.setDomainUserPassword(User2.username, "password2").get
        
        store.validateCredentials(User1.username, "password1").get shouldBe true
        store.validateCredentials(User2.username, "password2").get shouldBe true
      }
      
      "correctly set the passwords from hashes" in withPersistenceStore { store =>
        initUsers(store)
        
        store.setDomainUserPasswordHash(User1.username, "hash1").get // already set
        store.setDomainUserPasswordHash(User2.username, "hash2").get
        
        store.getDomainUserPasswordHash(User1.username).get.value shouldBe "hash1"
        store.getDomainUserPasswordHash(User2.username).get.value shouldBe "hash2"
      }

      "throw exception if user does not exist" in withPersistenceStore { store =>
        store.setDomainUserPassword("DoesNotExist", "doesn't matter").failure.exception shouldBe a[EntityNotFoundException]
      }
    }

    "validating credentials" must {
      "return true for a vaid usename and password" in withPersistenceStore { store =>
        initUsers(store)
        store.validateCredentials(User1.username, "password").get shouldBe true
      }

      "return false for an valid username and invalid password" in withPersistenceStore { store =>
        initUsers(store)
        store.validateCredentials(User1.username, "wrong").get shouldBe false
      }

      "return false for an invalid username" in withPersistenceStore { store =>
        initUsers(store)
        store.validateCredentials("no one", "p").get shouldBe false
      }
    }

    "setting last login" must {
      "updated last login doesn't fail" in withPersistenceStore { store =>
        initUsers(store)
        store.setLastLogin(DomainUserId.normal(User1.username), Instant.now()).get
      }
    }
    
    "creating a reconnect token" must {
      "correctly create a valid token" in withPersistenceStore { store =>
        initUsers(store)
        
        val token = store.createReconnectToken(User1.toUserId).get
        store.validateReconnectToken(token).get.value shouldBe User1.toUserId
      }

      "throw exception if user does not exist" in withPersistenceStore { store =>
        store.createReconnectToken(DomainUserId.normal("DoesNotExist")).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
  }

  def initUsers(store: DomainUserStore): Unit = {
    store.createDomainUser(User0)
    store.createDomainUser(User1)
    store.setDomainUserPassword(User1.username, "password")
    store.createDomainUser(User2)
  }
}