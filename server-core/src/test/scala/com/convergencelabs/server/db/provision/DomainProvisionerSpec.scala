package com.convergencelabs.server.db.provision

import org.scalatest.BeforeAndAfterAll
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpec
import com.convergencelabs.server.util.EmbeddedTestingOrientDB
import com.convergencelabs.server.domain.DomainId
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.mockito.MockitoSugar
import com.convergencelabs.server.datastore.convergence.DeltaHistoryStore
import org.mockito.Mockito
import org.mockito.Matchers
import scala.util.Success
import com.orientechnologies.orient.core.db.OrientDB

class DomainProvisionerSpec()
    extends WordSpec
    with BeforeAndAfterAll
    with MockitoSugar {

  val oriendDb = new EmbeddedTestingOrientDB("target/porvisionerdb", false)
  
  override def beforeAll(): Unit = {
    oriendDb.start()
  }

  override def afterAll(): Unit = {
    oriendDb.stop()
  }

  "A DomainProvisioner" when {
    "provisioning a domain" must {
      "Succfully provision a domain" in {
        val database = "DomainProvisionerTest"
        val store = mock[DeltaHistoryStore]
        Mockito.when(store.saveDomainDeltaHistory(Matchers.any())).thenReturn(Success(()))
        
        val provisioner = new DomainProvisioner(store, "remote:localhost", "root", "password", true)
        provisioner.provisionDomain(DomainId("some", "domain"), database, "writer", "wpassword", "admin", "apassword", false).get
        
        oriendDb.server.dropDatabase(database)
      }
    }
  }
}
