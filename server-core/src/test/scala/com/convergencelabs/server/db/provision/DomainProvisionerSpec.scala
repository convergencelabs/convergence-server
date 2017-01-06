package com.convergencelabs.server.db.provision

import org.scalatest.BeforeAndAfterAll
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpec
import com.convergencelabs.server.util.EmbeddedOrientDB
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.mock.MockitoSugar
import com.convergencelabs.server.datastore.DeltaHistoryStore
import org.mockito.Mockito
import org.mockito.Matchers
import scala.util.Success

class DomainProvisionerSpec()
    extends WordSpec
    with BeforeAndAfterAll
    with MockitoSugar {

  val oriendDb = new EmbeddedOrientDB("target/porvisionerdb", false)
  
  override def beforeAll(): Unit = {
    oriendDb.start()
  }

  override def afterAll(): Unit = {
    oriendDb.stop()
  }

  "A DomainProvisioner" when {
    "provisioning a domain" must {
      "Succfully provision a domain" in {
        val store = mock[DeltaHistoryStore]
        Mockito.when(store.saveDomainDeltaHistory(Matchers.any())).thenReturn(Success(()))
        
        val provisioner = new DomainProvisioner(store, "remote:localhost", "root", "password", true)
        provisioner.provisionDomain(DomainFqn("some", "domain"), "DomainProvisionerTest", "writer", "wpassword", "admin", "apassword", false).get
      }
    }
  }
}
