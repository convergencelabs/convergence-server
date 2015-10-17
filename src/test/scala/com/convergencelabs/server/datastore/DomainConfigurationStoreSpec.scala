package com.convergencelabs.server.datastore

import org.scalatest.WordSpec
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.common.log.OLogManager
import com.convergencelabs.server.domain.DomainFqn
import scala.util.Try

class DomainConfigurationStoreSpec extends WordSpec {

  OLogManager.instance().setConsoleLevel("WARNING")

  def initDB(uri: String): ODatabaseDocumentTx = {
    val db = new ODatabaseDocumentTx(uri)
    db.activateOnCurrentThread()
    db.create()
    val listener = new OCommandOutputListener() {
      def onMessage(iText: String) = {
      }
    }
    val file = getClass.getResource("/dbfiles/convergence.gz").getFile();
    val dbImport = new ODatabaseImport(db, file, listener)
    dbImport.importDatabase()
    dbImport.close()
    db
  }

  "An DomainConfigurationStore" when {

    "asked whether a domain exists" must {

      "return false if it doesn't exist" in {
        val db = initDB("memory:dcs1")
        val dbPool = new OPartitionedDatabasePool("memory:dcs1", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)
        assert(!domainConfigurationStore.domainExists(DomainFqn("notReal", "notReal")))
        db.activateOnCurrentThread()
        db.close()
      }

      "return true if it does exist" in {
        val db = initDB("memory:dcs2")
        val dbPool = new OPartitionedDatabasePool("memory:dcs2", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)
        assert(domainConfigurationStore.domainExists(DomainFqn("test", "test1")))
        db.activateOnCurrentThread()
        db.close()
      }
    }

    "retrieving a domain config by fqn" must {

      "return None if the domain doesn't exist" in {
        val db = initDB("memory:dcs3")
        val dbPool = new OPartitionedDatabasePool("memory:dcs3", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)
        assert(domainConfigurationStore.getDomainConfig(DomainFqn("notReal", "notReal")).isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }

      "return Some if the domain exist" in {
        val db = initDB("memory:dcs4")
        val dbPool = new OPartitionedDatabasePool("memory:dcs4", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)
        assert(!domainConfigurationStore.getDomainConfig(DomainFqn("test", "test1")).isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }
    }

    "retrieving a domain config by id" must {

      "return None if the domain doesn't exist" in {
        val db = initDB("memory:dcs5")
        val dbPool = new OPartitionedDatabasePool("memory:dcs5", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)
        assert(domainConfigurationStore.getDomainConfig("does not exist").isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }

      "return Some if the domain exist" in {
        val db = initDB("memory:dcs6")
        val dbPool = new OPartitionedDatabasePool("memory:dcs6", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)
        assert(!domainConfigurationStore.getDomainConfig("t1").isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }
    }

    "creating a domain" must {
      "insert the domain record into the database" in {
        val db = initDB("memory:dcs7")
        val dbPool = new OPartitionedDatabasePool("memory:dcs7", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)

        val domainConfig = DomainConfig("t4", DomainFqn("test", "test4"), "Test Domain 4", "root", "root", Map(), TokenKeyPair("private", "public"))
        domainConfigurationStore.createDomainConfig(domainConfig)
        assert(!domainConfigurationStore.getDomainConfig("t4").isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }

      "throw an exception if the domain exists" in {
        val db = initDB("memory:dcs8")
        val dbPool = new OPartitionedDatabasePool("memory:dcs8", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)

        val domainConfig = DomainConfig("t1", DomainFqn("test", "test1"), "Test Domain 1", "root", "root", Map(), TokenKeyPair("private", "public"))
        val createTry = Try(domainConfigurationStore.createDomainConfig(domainConfig))
        assert(createTry.isFailure)
        db.activateOnCurrentThread()
        db.close()
      }
    }

    "get domains by namespace" must {
      "return all domains for a namespace" in {
        val db = initDB("memory:dcs9")
        val dbPool = new OPartitionedDatabasePool("memory:dcs9", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)
        assert(domainConfigurationStore.getDomainConfigsInNamespace("test").size == 3)
        db.activateOnCurrentThread()
        db.close()
      }
    }

    "removing a domain" must {
      "remove the domain record in the database if it exists" in {
        val db = initDB("memory:dcs10")
        val dbPool = new OPartitionedDatabasePool("memory:dcs10", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)
        domainConfigurationStore.removeDomainConfig("t1")
        assert(!domainConfigurationStore.getDomainConfig("t1").isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }

      "not throw an exception if the domain does not exist" in {
        val db = initDB("memory:dcs11")
        val dbPool = new OPartitionedDatabasePool("memory:dcs11", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)
        val removeTry = Try(domainConfigurationStore.removeDomainConfig("doesn't exist"))
        assert(removeTry.isSuccess)
        db.activateOnCurrentThread()
        db.close()
      }
    }

  }
}