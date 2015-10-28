package com.convergencelabs.server.datastore.domain

import org.scalatest.WordSpec
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.BeforeAndAfterAll
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.common.log.OLogManager
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JNull
import org.json4s.JsonAST.JInt
import java.text.SimpleDateFormat
import java.time.Instant
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

class DomainUserStoreSpec
    extends WordSpec
    with PersistenceStoreSpec[DomainUserStore] {

  def createStore(dbPool: OPartitionedDatabasePool): DomainUserStore = new DomainUserStore(dbPool)
  
  "A DomainUserStore" when {
    "when creating a user" must {
      "be able to get the user that was created" in withPersistenceStore { store =>
        val created = DomainUser("u10", "newUser", "new", "user", "newUser@example.com")
        store.createDomainUser(created, None)

        val queried = store.getDomainUserByUid("u10")
        assert(queried.isDefined)
        assert(created == queried.get)
      }

      "not allow duplicate uids" in withPersistenceStore { store =>
        val original = DomainUser("u10", "newUser", "new", "user", "newUser@example.com")
        store.createDomainUser(original, None)

        val duplicateUid = DomainUser("u10", "newUser1", "new1", "user1", "newUser1@example.com")
        intercept[ORecordDuplicatedException] {
          store.createDomainUser(duplicateUid, None)
        }
      }
      
      "not allow duplicate usernames" in withPersistenceStore { store =>
        val original = DomainUser("u10", "newUser", "new", "user", "newUser@example.com")
        store.createDomainUser(original, None)

        val duplicateUid = DomainUser("u11", "newUser", "new1", "user1", "newUser1@example.com")
        intercept[ORecordDuplicatedException] {
          store.createDomainUser(duplicateUid, None)
        }
      }
      
      "not allow duplicate emails" in withPersistenceStore { store =>
        val original = DomainUser("u10", "newUser", "new", "user", "newUser@example.com")
        store.createDomainUser(original, None)

        val duplicateUid = DomainUser("u11", "newUser1", "new1", "user1", "newUser@example.com")
        intercept[ORecordDuplicatedException] {
          store.createDomainUser(duplicateUid, None)
        }
      }

      "correctly set the password, if one was provided" in withPersistenceStore { store =>
        val passwd = "newPassword"
        val created = DomainUser("u10", "newUser", "new", "user", "newUser@example.com")
        store.createDomainUser(created, Some(passwd))

        assert(store.validateCredentials("newUser", passwd))
      }
    }
  }
}