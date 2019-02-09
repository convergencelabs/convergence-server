package com.convergencelabs.server.datastore.convergence

import java.time.Duration

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.datastore.convergence.schema.UserClass
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.db.schema.DeltaCategory

class UserStoreSpec
    extends PersistenceStoreSpec[UserStore](DeltaCategory.Convergence)
    with WordSpecLike
    with Matchers {

  val username = "test1"
  val DisplayName = "test one"
  val Password = "password"
  val BearerToken = "bearerToken"

  val TestUser = User(username, "test1@example.com", username, username, DisplayName, None)
  val TestUser2 = User("testUser2", "test2@example.com", "test", "two", "test two", None)

  def createStore(dbProvider: DatabaseProvider): UserStore = new UserStore(dbProvider)

  "A UserStore" when {
    "querying a user" must {
      "correctly retreive user by username" in withPersistenceStore { store =>
        store.createUser(TestUser, Password, BearerToken).get
        val queried = store.getUserByUsername(username)
        queried.success.get.value shouldBe TestUser
      }
    }

    "checking whether a user exists" must {
      "return true if the user exist" in withPersistenceStore { store =>
        store.createUser(TestUser, Password, BearerToken).get
        store.userExists(TestUser.username).get shouldBe true
      }

      "return false if the user does not exist" in withPersistenceStore { store =>
        store.userExists("DoesNotExist").get shouldBe false
      }
    }
    
    "updating a user" must {
      "update an existing user" in withPersistenceStore { store =>
        store.createUser(TestUser, Password, BearerToken).get
        val update = User(TestUser.username, "first", "last", "display", "email", None)
        store.updateUser(update).get
        val queried = store.getUserByUsername(TestUser.username).get.value
        
        queried shouldBe update
      }

      "fail with a EntityNotFoundExcpetion if the user does not exist" in withPersistenceStore { store =>
        val update = User(TestUser.username, "first", "last", "display", "email", None)
        store.updateUser(update).failure.exception shouldBe a[EntityNotFoundException]
      }
      
      "fail with a DuplicateValue when updating to a username that is taken" in withPersistenceStore { store =>
        store.createUser(TestUser, Password, BearerToken).get
        store.createUser(TestUser2, Password, BearerToken).get
        val update = TestUser2.copy(email = TestUser.email)
        val exception = store.updateUser(update).failure.exception
        exception shouldBe a[DuplicateValueException]
        exception.asInstanceOf[DuplicateValueException].field shouldBe UserClass.Fields.Email
      }
    }

    "setting a users password" must {
      "correctly set the password" in withPersistenceStore { store =>
        val password = "newPasswordToSet"
        store.createUser(TestUser, password, BearerToken).get
        store.setUserPassword(username, password).success
        store.validateCredentials(username, password).success.get shouldBe true
      }

      "return a failure if user does not exist" in withPersistenceStore { store =>
        store.setUserPassword("DoesNotExist", "doesn't matter").failed.get shouldBe a[EntityNotFoundException]
      }
    }
    
    "getting a user's password hash" must {
      "return a hash for an existing user." in withPersistenceStore { store =>
        val password = "newPasswordToSet"
        store.createUser(TestUser, password, BearerToken).get
        store.getUserPasswordHash(username).get shouldBe defined
      }

      "return None if user does not exist" in withPersistenceStore { store =>
        store.getUserPasswordHash("DoesNotExist").get shouldBe None
      }
    }

    "validating credentials" must {
      "return true and a username for a valid usename and password" in withPersistenceStore { store =>
        store.createUser(TestUser, Password, BearerToken).get
        store.validateCredentials(username, Password).success.get shouldBe true
      }

      "return false and None for an valid username and invalid password" in withPersistenceStore { store =>
        store.createUser(TestUser, Password, BearerToken).get
        store.validateCredentials(username, "wrong").success.get shouldBe false
      }

      "return false and None for an invalid username" in withPersistenceStore { store =>
        store.createUser(TestUser, Password, BearerToken).get
        store.validateCredentials("no one", "p").success.value shouldBe false
      }
    }
  }
}
