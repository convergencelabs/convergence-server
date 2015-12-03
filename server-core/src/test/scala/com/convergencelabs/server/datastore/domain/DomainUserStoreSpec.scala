package com.convergencelabs.server.datastore.domain

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.convergencelabs.server.domain.DomainUser

class DomainUserStoreSpec
    extends PersistenceStoreSpec[DomainUserStore]("/dbfiles/domain.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): DomainUserStore = new DomainUserStore(dbPool)

  "A DomainUserStore" when {
    "when creating a user" must {
      "be able to get the user that was created" in withPersistenceStore { store =>

        val created = DomainUser("u10", "newUser", "new", "user", "newUser@example.com")
        store.createDomainUser(created, None).success

        val queried = store.getDomainUserByUid("u10")
        queried.success.get.value shouldBe created
      }

      "not allow duplicate uids" in withPersistenceStore { store =>
        val original = DomainUser("u10", "newUser", "new", "user", "newUser@example.com")
        store.createDomainUser(original, None).success

        val duplicateUid = DomainUser("u10", "newUser1", "new1", "user1", "newUser1@example.com")
        store.createDomainUser(duplicateUid, None).failed.get shouldBe a[ORecordDuplicatedException]
      }

      "not allow duplicate usernames" in withPersistenceStore { store =>
        val original = DomainUser("u10", "newUser", "new", "user", "newUser@example.com")
        store.createDomainUser(original, None).success

        val duplicateUid = DomainUser("u11", "newUser", "new1", "user1", "newUser1@example.com")
        store.createDomainUser(duplicateUid, None).failed.get shouldBe a[ORecordDuplicatedException]
      }

      "not allow duplicate emails" in withPersistenceStore { store =>
        val original = DomainUser("u10", "newUser", "new", "user", "newUser@example.com")
        store.createDomainUser(original, None).success

        val duplicateUid = DomainUser("u11", "newUser1", "new1", "user1", "newUser@example.com")
        store.createDomainUser(duplicateUid, None).failed.get shouldBe a[ORecordDuplicatedException]
      }

      "correctly set the password, if one was provided" in withPersistenceStore { store =>
        val passwd = "newPassword"
        val created = DomainUser("u10", "newUser", "new", "user", "newUser@example.com")
        store.createDomainUser(created, Some(passwd)).success

        store.validateCredentials("newUser", passwd).success.get shouldBe (true, Some("u10"))
        store.validateCredentials("newUser", "notCorrect").success.get shouldBe (false, None)
      }
    }
  }
}