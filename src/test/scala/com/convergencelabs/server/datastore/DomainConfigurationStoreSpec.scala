package com.convergencelabs.server.datastore

import org.scalatest.WordSpec

import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport

class DomainConfigurationStoreSpec extends WordSpec {

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
  }
}