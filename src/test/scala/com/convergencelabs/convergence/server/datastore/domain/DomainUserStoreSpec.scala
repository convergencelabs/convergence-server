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

package com.convergencelabs.convergence.server.datastore.domain

import java.time.Instant

import com.convergencelabs.convergence.server.datastore.domain.DomainUserStore.{CreateNormalDomainUser, UpdateDomainUser}
import com.convergencelabs.convergence.server.datastore.{DuplicateValueException, EntityNotFoundException, SortOrder}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.db.schema.DeltaCategory
import com.convergencelabs.convergence.server.domain.{DomainUser, DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class DomainUserStoreSpec
    extends PersistenceStoreSpec[DomainUserStore](DeltaCategory.Domain)
    with AnyWordSpecLike
    with Matchers {

  // Pre-loaded Users
  private val User0 = DomainUser(DomainUserType.Convergence, "admin", Some("Admin"), Some("User"), Some("Admin User"), Some("admin@example.com"), None)
  private val User1 = DomainUser(DomainUserType.Normal, "test1", Some("Test"), Some("One"), Some("Test One"), Some("test1@example.com"), None)
  private val User2 = DomainUser(DomainUserType.Normal, "test2", Some("Test"), Some("Two"), Some("Test Two"), Some("test2@example.com"), None)

  // New Users
  private val User10 = CreateNormalDomainUser("user10", Some("first10"), Some("last10"), Some("first10 last10"), Some("user10@example.com"))
  private val User11 = CreateNormalDomainUser("user11", Some("first11"), Some("last11"), Some("first11 last11"), Some("user11@example.com"))
  private val User12 = CreateNormalDomainUser("user12", None, None, None, None)

  def createStore(dbProvider: DatabaseProvider): DomainUserStore = new DomainUserStore(dbProvider)

  "A DomainUserStore" when {
    "creating a user" must {
      "be able to get the user that was created" in withPersistenceStore { store =>
        store.createNormalDomainUser(User10).success

        val queried = store.getNormalDomainUser(User10.username)
        val DomainUser(_, username, fname, lname, displayName, email, _, _, _, _) = queried.success.get.value
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
        val DomainUser(_, username, fname, lname, displayName, email, _, _, _, _) = queried.success.get.value
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
        store.updateDomainUser(UpdateDomainUser(DomainUserId.normal("foo"), None, None, None, None, None)).failure.exception shouldBe a[EntityNotFoundException]
      }

      "currectly update an existing user, if unique properties are not violated" in withPersistenceStore { store =>
        initUsers(store)
        val update = UpdateDomainUser(DomainUserId.normal(User1.username), Some("f"), Some("l"), Some("d"), Some("e"), Some(true))
        val updated = DomainUser(DomainUserType.Normal, User1.username, Some("f"), Some("l"), Some("d"), Some("e"), None, disabled = true, deleted = false, None)
        store.updateDomainUser(update).get
        val queried = store.getNormalDomainUser(User1.username).get.get
        queried shouldBe updated
      }
    }

    // FIXME check paged data values.
    "retrieving all users" must {
      "order correctly by username" in withPersistenceStore { store =>
        initUsers(store)
        val allUsers = store.getAllDomainUsers(None, None, QueryOffset(), QueryLimit()).get
        val orderedDescending = store.getAllDomainUsers(Some(DomainUserField.Username), Some(SortOrder.Descending), QueryOffset(), QueryLimit()).success.get
        val orderedAscending = store.getAllDomainUsers(Some(DomainUserField.Username), Some(SortOrder.Ascending), QueryOffset(), QueryLimit()).success.get

        orderedDescending.data shouldBe allUsers.data.sortWith(_.username > _.username)
        orderedAscending.data shouldBe orderedDescending.data.reverse
      }

      "limit results to the correct number" in withPersistenceStore { store =>
        initUsers(store)
        val allUser = store.getAllDomainUsers(None, None, QueryOffset(), QueryLimit()).get
        store.getAllDomainUsers(None, None, QueryOffset(), QueryLimit(2)).success.get.data shouldBe allUser.data.slice(0, 2)
      }
    }

    "searching for users by fields" must {
      "return multiple matches if a prefix is supplied" in withPersistenceStore { store =>
        initUsers(store)
        val fields = List(DomainUserField.Username)
        val searchString = "test"
        val users = store.searchUsersByFields(fields, searchString, None, None, QueryOffset(), QueryLimit()).get
        users.data.length shouldBe 2
      }

      "return a single user if only one user matches" in withPersistenceStore { store =>
        initUsers(store)
        val fields = List(DomainUserField.Username)
        val searchString = "test1"
        val users = store.searchUsersByFields(fields, searchString, None, None, QueryOffset(), QueryLimit()).get
        users.data.length shouldBe 1
        users.data.head shouldBe User1
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