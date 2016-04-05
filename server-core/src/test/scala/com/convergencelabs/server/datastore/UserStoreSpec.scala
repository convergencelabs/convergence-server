package com.convergencelabs.server.datastore

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.convergencelabs.server.User
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import java.time.Instant
import java.util.Date

class UserStoreSpec
    extends PersistenceStoreSpec[UserStore]("/dbfiles/convergence.json.gz")
    with WordSpecLike
    with Matchers {

  // Pre-loaded Users
  val User0 = User("cu0", "test")

  def createStore(dbPool: OPartitionedDatabasePool): UserStore = new UserStore(dbPool)

  "A DomainUserStore" when {
    "querying a user" must {
      "correctly retreive user by uid" in withPersistenceStore { store =>
        val queried = store.getUserByUid(User0.uid)
        queried.success.get.value shouldBe User0
      }

      "correctly retreive user by username" in withPersistenceStore { store =>
        val queried = store.getUserByUsername(User0.username)
        queried.success.get.value shouldBe User0
      }
    }

    "checking whether a user exists" must {
      "return true if the user exist" in withPersistenceStore { store =>
        store.userExists(User0.username).success.get shouldBe true
      }

      "return false if the user does not exist" in withPersistenceStore { store =>
        store.userExists("DoesNotExist").success.get shouldBe false
      }
    }

    "setting a users password" must {
      "correctly set the password" in withPersistenceStore { store =>
        val password = "newPasswordToSet"
        store.setUserPassword(User0.username, password).success
        store.validateCredentials(User0.username, password).success.get shouldBe (true, Some("cu0"))
      }

      "throw exception if user does not exist" in withPersistenceStore { store =>
        store.setUserPassword("DoesNotExist", "doesn't matter").failed.get shouldBe a[IllegalArgumentException]
      }
    }

    "validating credentials" must {
      "return true and a uid for a valid usename and password" in withPersistenceStore { store =>
        store.validateCredentials(User0.username, "password").success.value shouldBe (true, Some("cu0"))
      }

      "return false and None for an valid username and invalid password" in withPersistenceStore { store =>
        store.validateCredentials(User0.username, "wrong").success.value shouldBe (false, None)
      }

      "return false and None for an invalid username" in withPersistenceStore { store =>
        store.validateCredentials("no one", "p").success.value shouldBe (false, None)
      }
    }
    
    "validating tokens" must {
      "return true and a uid for a valid token" in withPersistenceStore { store =>
        store.createToken(User0.uid, "myToken", Date.from(Instant.now().plusSeconds(100))) 
        store.validateToken("myToken").success.value shouldBe (true, Some("cu0"))
      }

      "return false and None for an expired token" in withPersistenceStore { store =>
        val expireTime = Instant.now().minusSeconds(1)
        store.createToken(User0.uid, "myToken", Date.from(expireTime))
        store.validateToken("myToken").success.value shouldBe (false, None)
      }

      "return false and None for an invalid token" in withPersistenceStore { store =>
        store.validateToken("myToken").success.value shouldBe (false, None)
      }
    }
  }
}
