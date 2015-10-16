package com.convergencelabs.server.datastore

import org.scalatest.WordSpec
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.DomainFqn

class DomainConfigurationStoreSpec extends WordSpec {
  val defaultCommandListener = new OCommandOutputListener() {
    def onMessage(iText: String) = {}
  }

  "An DomainConfigurationStore" when {
    "asked whether a domain exists" must {
      "return false if it doesn't exist" in {
        val db = new ODatabaseDocumentTx("memory:dcs1")
        db.activateOnCurrentThread()
        db.create()
        val file = getClass.getResource("/dbfiles/convergence.gz").getFile();
        val dbImport = new ODatabaseImport(db, file, defaultCommandListener)
        dbImport.importDatabase()
        dbImport.close()

        val dbPool = new OPartitionedDatabasePool("memory:dcs1", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)
        assert(!domainConfigurationStore.domainExists(DomainFqn("notReal", "notReal")))
      }

      "return true if it does exist" in {
        val db = new ODatabaseDocumentTx("memory:dcs2")
        db.activateOnCurrentThread()
        db.create()
        val file = getClass.getResource("/dbfiles/convergence.gz").getFile();
        val dbImport = new ODatabaseImport(db, file, defaultCommandListener)
        dbImport.importDatabase()
        dbImport.close()

        val dbPool = new OPartitionedDatabasePool("memory:dcs2", "admin", "admin")
        val domainConfigurationStore = new DomainConfigurationStore(dbPool)
        assert(domainConfigurationStore.domainExists(DomainFqn("test", "test1")))
      }
    }
  }
}