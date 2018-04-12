package com.convergencelabs.server.datastore

import java.time.Duration
import java.time.Instant

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.datastore.UserStore.User

class UserStoreSpec
    extends PersistenceStoreSpec[UserStore](DeltaCategory.Convergence)
    with WordSpecLike
    with Matchers {

  val username = "test1"
  val displayName = "test one"
  val password = "password"

  val DummyToken = "myToken"
  val TestUser = User(username, "test1@example.com", username, username, displayName)
  val TestUser2 = User("testUser2", "test2@example.com", "test", "two", "test two")
  val tokenDurationMinutes = 5
  val tokenDuration = Duration.ofSeconds(5) // scalastyle:ignore magic.number

  def createStore(dbProvider: DatabaseProvider): UserStore = new UserStore(dbProvider, tokenDuration)

  "A UserStore" when {
    "querying a user" must {
      "correctly retreive user by username" in withPersistenceStore { store =>
        store.createUser(TestUser, password).get
        val queried = store.getUserByUsername(username)
        queried.success.get.value shouldBe TestUser
      }
    }

    "checking whether a user exists" must {
      "return true if the user exist" in withPersistenceStore { store =>
        store.createUser(TestUser, password).get
        store.userExists(TestUser.username).get shouldBe true
      }

      "return false if the user does not exist" in withPersistenceStore { store =>
        store.userExists("DoesNotExist").get shouldBe false
      }
    }
    
    "updating a user" must {
      "update an existing user" in withPersistenceStore { store =>
        store.createUser(TestUser, password).get
        val update = User(TestUser.username, "first", "last", "display", "email")
        store.updateUser(update).get
        val queried = store.getUserByUsername(TestUser.username).get.value
        
        queried shouldBe update
      }

      "fail with a EntityNotFoundExcpetion if the user does not exist" in withPersistenceStore { store =>
        val update = User(TestUser.username, "first", "last", "display", "email")
        store.updateUser(update).failure.exception shouldBe a[EntityNotFoundException]
      }
      
      "fail with a DuplicateValue when updating to a username that is taken" in withPersistenceStore { store =>
        store.createUser(TestUser, password).get
        store.createUser(TestUser2, password).get
        val update = TestUser2.copy(email = TestUser.email)
        val exception = store.updateUser(update).failure.exception
        exception shouldBe a[DuplicateValueException]
        exception.asInstanceOf[DuplicateValueException].field shouldBe UserStore.Fields.Email
      }
    }

    "setting a users password" must {
      "correctly set the password" in withPersistenceStore { store =>
        val password = "newPasswordToSet"
        store.createUser(TestUser, password).get
        store.setUserPassword(username, password).success
        store.validateCredentials(username, password).success.get shouldBe defined
      }

      "return a failure if user does not exist" in withPersistenceStore { store =>
        store.setUserPassword("DoesNotExist", "doesn't matter").failed.get shouldBe a[EntityNotFoundException]
      }
    }
    
    "getting a user's password hash" must {
      "return a hash for an existing user." in withPersistenceStore { store =>
        val password = "newPasswordToSet"
        store.createUser(TestUser, password).get
        store.getUserPasswordHash(username).get shouldBe defined
      }

      "return None if user does not exist" in withPersistenceStore { store =>
        store.getUserPasswordHash("DoesNotExist").get shouldBe None
      }
    }

    "validating credentials" must {
      "return true and a username for a valid usename and password" in withPersistenceStore { store =>
        store.createUser(TestUser, password).get
        store.validateCredentials(username, password).success.get shouldBe defined
      }

      "return false and None for an valid username and invalid password" in withPersistenceStore { store =>
        store.createUser(TestUser, password).get
        store.validateCredentials(username, "wrong").success.value shouldBe None
      }

      "return false and None for an invalid username" in withPersistenceStore { store =>
        store.createUser(TestUser, password).get
        store.validateCredentials("no one", "p").success.value shouldBe None
      }
    }

    "validating tokens" must {
      "return true and a uid for a valid token" in withPersistenceStore { store =>
        store.createUser(TestUser, password).get
        store.createToken(username, DummyToken, Instant.now().plusSeconds(100)) // scalastyle:ignore magic.number
        store.validateUserSessionToken(DummyToken).success.value shouldBe Some(username)
      }

      "return false and None for an expired token" in withPersistenceStore { store =>
        store.createUser(TestUser, password).get
        val expireTime = Instant.now().minusSeconds(1)
        store.createToken(username, DummyToken, expireTime)
        store.validateUserSessionToken(DummyToken).success.value shouldBe None
      }

      "return false and None for an invalid token" in withPersistenceStore { store =>
        store.validateUserSessionToken(DummyToken).success.value shouldBe None
      }
    }
  }
}
