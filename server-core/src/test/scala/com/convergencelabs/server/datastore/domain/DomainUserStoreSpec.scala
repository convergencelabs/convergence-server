package com.convergencelabs.server.datastore.domain

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.datastore.SortOrder

class DomainUserStoreSpec
    extends PersistenceStoreSpec[DomainUserStore]("/dbfiles/domain.json.gz")
    with WordSpecLike
    with Matchers {

  val u1Id = "u1"

  // Pre-loaded Users
  val User0 = DomainUser("u0", "admin", Some("firstAdmin"), Some("lastAdmin"), Some("admin@example.com"))
  val User1 = DomainUser(u1Id, "test1", Some("Test1"), Some("One"), Some("test1@example.com"))
  val User2 = DomainUser("u2", "test2", Some("Test2"), Some("Two"), Some("test2@example.com"))

  // New Users
  val User10 = DomainUser("u10", "user10", Some("first10"), Some("last10"), Some("user10@example.com"))
  val User11 = DomainUser("u11", "user11", Some("first11"), Some("last11"), Some("user11@example.com"))
  val User12 = DomainUser("u12", "user12", None, None, None)

  def createStore(dbPool: OPartitionedDatabasePool): DomainUserStore = new DomainUserStore(dbPool)

  "A DomainUserStore" when {
    "creating a user" must {
      "be able to get the user that was created" in withPersistenceStore { store =>
        store.createDomainUser(User10, None).success

        val queried = store.getDomainUserByUid(User10.uid)
        queried.success.get.value shouldBe User10
      }

      "not allow duplicate uids" in withPersistenceStore { store =>
        store.createDomainUser(User10, None).success
        val duplicate = DomainUser(User10.uid, User11.username, User11.firstName, User11.lastName, User11.email)
        store.createDomainUser(duplicate, None).failed.get shouldBe a[ORecordDuplicatedException]
      }

      "not allow duplicate usernames" in withPersistenceStore { store =>
        store.createDomainUser(User10, None).success

        val duplicate = DomainUser(User11.uid, User10.username, User11.firstName, User11.lastName, User11.email)
        store.createDomainUser(duplicate, None).failed.get shouldBe a[ORecordDuplicatedException]
      }

      "not allow duplicate emails" in withPersistenceStore { store =>
        store.createDomainUser(User10, None).success

        val duplicate = DomainUser(User11.uid, User11.username, User11.firstName, User11.lastName, User10.email)
        store.createDomainUser(duplicate, None).failed.get shouldBe a[ORecordDuplicatedException]
      }

      "correctly set the password, if one was provided" in withPersistenceStore { store =>
        val passwd = "newPassword"
        store.createDomainUser(User10, Some(passwd)).success

        store.validateCredentials(User10.username, passwd).success.get shouldBe (true, Some(User10.uid))
        store.validateCredentials(User10.username, "notCorrect").success.get shouldBe (false, None)
      }

      "allow creation of users with only uid and username" in withPersistenceStore { store =>
        store.createDomainUser(User12, None).success

        val queried = store.getDomainUserByUid(User12.uid)
        queried.success.get.value shouldBe User12
      }
    }

    "removing a user" must {
      "correctly remove the user by uid" in withPersistenceStore { store =>
        store.deleteDomainUser(User1.uid).success
        val queried = store.getDomainUserByUid(User1.uid)
        queried.success.get shouldBe None
      }

      "not throw exception if user does not exist" in withPersistenceStore { store =>
        store.deleteDomainUser("DoesNotExit").success
      }
    }

    "querying a user" must {
      "correctly retreive user by uid" in withPersistenceStore { store =>
        val queried = store.getDomainUserByUid(User1.uid)
        queried.success.get.value shouldBe User1
      }

      "correctly retreive user by username" in withPersistenceStore { store =>
        val queried = store.getDomainUserByUsername(User1.username)
        queried.success.get.value shouldBe User1
      }

      "correctly retreive user by email" in withPersistenceStore { store =>
        val queried = store.getDomainUserByEmail(User1.email.value)
        queried.success.get.value shouldBe User1
      }
    }

    "querying multiple users" must {
      "correctly retreive users by uid" in withPersistenceStore { store =>
        val queried = store.getDomainUsersByUid(List(User1.uid, User2.uid))
        queried.success.value should contain allOf (User1, User2)
      }

      "correctly retreive users by username" in withPersistenceStore { store =>
        val queried = store.getDomainUsersByUsername(List(User1.username, User2.username))
        queried.success.value should contain allOf (User1, User2)
      }

      "correctly retreive users by email" in withPersistenceStore { store =>
        val queried = store.getDomainUsersByEmail(List(User1.email.value, User2.email.value))
        queried.success.value should contain allOf (User1, User2)
      }
    }

    "updating user" must {
      "not allow setting duplicate email" in withPersistenceStore { store =>
        store.createDomainUser(User10, None).success
        store.createDomainUser(User11, None).success
        val original2Dup = DomainUser(User11.uid, User11.username, User11.firstName, User11.lastName, User10.email)
        store.updateDomainUser(original2Dup).failed.get shouldBe a[ORecordDuplicatedException]
      }

      "not allow setting duplicate username" in withPersistenceStore { store =>
        store.createDomainUser(User10, None).success
        store.createDomainUser(User11, None).success
        val original2Dup = DomainUser(User11.uid, User10.username, User11.firstName, User11.lastName, User11.email)
        store.updateDomainUser(original2Dup).failed.get shouldBe a[ORecordDuplicatedException]
      }

      "throw exception if user does not exist" in withPersistenceStore { store =>
        store.updateDomainUser(User10).failed.get shouldBe a[IllegalArgumentException]
      }

      "currectly update an existing user, if unique properties are not violoated" in withPersistenceStore { store =>
        val updated = DomainUser(User1.uid, User1.username, Some("f"), Some("l"), Some("e"))
        store.updateDomainUser(updated).success
        val queried = store.getDomainUserByUsername(User1.username).success.value.get
        queried shouldBe updated
      }
    }

    "retreiving all users" must {
      "order correctly by username" in withPersistenceStore { store =>
        val allUsers = store.getAllDomainUsers(None, None, None, None).success.value
        val orderedDescending = store.getAllDomainUsers(Some(DomainUserField.Username), Some(SortOrder.Descending), None, None).success.get
        val orderedAscending = store.getAllDomainUsers(Some(DomainUserField.Username), Some(SortOrder.Ascending), None, None).success.get

        orderedDescending shouldBe allUsers.sortWith(_.username > _.username)
        orderedAscending shouldBe orderedDescending.reverse
      }

      "limit results to the correct number" in withPersistenceStore { store =>
        val allUser = store.getAllDomainUsers(None, None, None, None).success.value
        store.getAllDomainUsers(None, None, Some(2), None).success.get shouldBe allUser.slice(0, 2)
      }
    }

    "searching for users by fields" must {
      "should return multiple matches if a prefix is supplied" in withPersistenceStore { store =>
        val fields = List(DomainUserField.UserId)
        val searchString = "u"
        val users = store.searchUsersByFields(fields, searchString, None, None, None, None).success.value
        users.length shouldBe 5
      }

      "should a single user if only one user matches" in withPersistenceStore { store =>
        val fields = List(DomainUserField.UserId)
        val searchString = u1Id
        val users = store.searchUsersByFields(fields, searchString, None, None, None, None).success.value
        users.length shouldBe 1
        users(0) shouldBe User1
      }
    }

    "getting users users by fields" must {
      "should return multiple matches if a prefix is supplied" in withPersistenceStore { store =>
        val fields = List(DomainUserField.UserId)
        val searchString = "u"
        val users = store.searchUsersByFields(fields, searchString, None, None, None, None).success.value
        users.length shouldBe 5
      }

      "should a single user if only one user matches" in withPersistenceStore { store =>
        val fields = List(DomainUserField.UserId)
        val searchString = u1Id
        val users = store.searchUsersByFields(fields, searchString, None, None, None, None).success.value
        users.length shouldBe 1
        users(0) shouldBe User1
      }
    }

    "checking whether a user exists" must {
      "return true if the user exist" in withPersistenceStore { store =>
        store.domainUserExists(User1.username).success.get shouldBe true
      }

      "return false if the user does not exist" in withPersistenceStore { store =>
        store.domainUserExists("DoesNotExist").success.get shouldBe false
      }
    }

    "setting a users password" must {
      "correctly set the password" in withPersistenceStore { store =>
        val password = "newPasswordToSet"
        store.setDomainUserPassword(User1.username, password).success
        store.validateCredentials(User1.username, password).success.get shouldBe (true, Some(u1Id))
      }

      "throw exception if user does not exist" in withPersistenceStore { store =>
        store.setDomainUserPassword("DoesNotExist", "doesn't matter").failed.get shouldBe a[IllegalArgumentException]
      }
    }

    "validating credentials" must {
      "return true and a uid for a vaid usename and passwordr" in withPersistenceStore { store =>
        store.validateCredentials(User1.username, "password").success.value shouldBe (true, Some(u1Id))
      }

      "return false and None for an valid username and invalid password" in withPersistenceStore { store =>
        store.validateCredentials(User1.username, "wrong").success.value shouldBe (false, None)
      }

      "return false and None for an invalid username" in withPersistenceStore { store =>
        store.validateCredentials("no one", "p").success.value shouldBe (false, None)
      }
    }
  }
}
