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

  val User1Uid = "u10"
  val User1Username = "user10"
  val User1FirstName = Some("first10")
  val User1LastName = Some("last10")
  val User1Email = Some("user10@example.com")

  val User2Uid = "u11"
  val User2Username = "user11"
  val User2FirstName = Some("first11")
  val User2LastName = Some("last11")
  val User2Email = Some("user11@example.com")

  def createStore(dbPool: OPartitionedDatabasePool): DomainUserStore = new DomainUserStore(dbPool)

  "A DomainUserStore" when {
    "when creating a user" must {
      "be able to get the user that was created" in withPersistenceStore { store =>

        val created = DomainUser(User1Uid, User1Username, User1FirstName, User1LastName, User1Email)
        store.createDomainUser(created, None).success

        val queried = store.getDomainUserByUid(User1Uid)
        queried.success.get.value shouldBe created
      }

      "not allow duplicate uids" in withPersistenceStore { store =>
        val original = DomainUser(User1Uid, User1Username, User1FirstName, User1LastName, User1Email)
        store.createDomainUser(original, None).success

        val duplicate = DomainUser(User1Uid, User2Username, User2FirstName, User2LastName, User2Email)
        store.createDomainUser(duplicate, None).failed.get shouldBe a[ORecordDuplicatedException]
      }

      "not allow duplicate usernames" in withPersistenceStore { store =>
        val original = DomainUser(User1Uid, User1Username, User1FirstName, User1LastName, User1Email)
        store.createDomainUser(original, None).success

        val duplicate = DomainUser(User2Uid, User1Username, User2FirstName, User2LastName, User2Email)
        store.createDomainUser(duplicate, None).failed.get shouldBe a[ORecordDuplicatedException]
      }

      "not allow duplicate emails" in withPersistenceStore { store =>
        val original = DomainUser(User1Uid, User1Username, User1FirstName, User1LastName, User1Email)
        store.createDomainUser(original, None).success

        val duplicate = DomainUser(User2Uid, User2Username, User2FirstName, User2LastName, User1Email)
        store.createDomainUser(duplicate, None).failed.get shouldBe a[ORecordDuplicatedException]
      }

      "correctly set the password, if one was provided" in withPersistenceStore { store =>
        val passwd = "newPassword"
        val created = DomainUser(User1Uid, User1Username, User1FirstName, User1LastName, User1Email)
        store.createDomainUser(created, Some(passwd)).success

        store.validateCredentials(User1Username, passwd).success.get shouldBe (true, Some(User1Uid))
        store.validateCredentials(User1Username, "notCorrect").success.get shouldBe (false, None)
      }
    }
  }
}
