package com.convergencelabs.server.datastore

import org.scalatest.WordSpec
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

class ConfigurationStoreSpec extends WordSpec {
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

  "An ConfigurationStore" when {

    "asked for a config" must {

      "return None if it doesn't exist" in {
        val db = initDB("memory:cs1")
        val dbPool = new OPartitionedDatabasePool("memory:cs1", "admin", "admin")
        val configurationStore = new ConfigurationStore(dbPool)
        assert(configurationStore.getConfiguration("not real").isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }

      "return Some if it does exist" in {
        val db = initDB("memory:cs2")
        val dbPool = new OPartitionedDatabasePool("memory:cs2", "admin", "admin")
        val configurationStore = new ConfigurationStore(dbPool)
        assert(!configurationStore.getConfiguration("connection").isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }
      
      "return Some[ConnectionConfig] if asked for Connection Config" in {
        val db = initDB("memory:cs3")
        val dbPool = new OPartitionedDatabasePool("memory:cs3", "admin", "admin")
        val configurationStore = new ConfigurationStore(dbPool)
        val config = configurationStore.getConnectionConfig()
        assert(!configurationStore.getConnectionConfig().isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }
      
      "return Some[RestConfig] if asked for Rest Config" in {
        val db = initDB("memory:cs4")
        val dbPool = new OPartitionedDatabasePool("memory:cs4", "admin", "admin")
        val configurationStore = new ConfigurationStore(dbPool)
        assert(!configurationStore.getRestConfig().isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }
      
    }
  }
}