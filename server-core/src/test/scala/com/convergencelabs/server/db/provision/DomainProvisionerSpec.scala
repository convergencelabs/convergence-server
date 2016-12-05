package com.convergencelabs.server.db.provision

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpec
import com.convergencelabs.server.util.EmbeddedOrientDB
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.mock.MockitoSugar
import com.convergencelabs.server.datastore.DeltaHistoryStore

class DomainProvisionerSpec()
    extends WordSpec
    with Matchers
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
        val provisioner = new DomainProvisioner(store, "remote:localhost", "root", "password", true)
        provisioner.provisionDomain(DomainFqn("some", "domain"), "DomainProvisionerTest", "writer", "wpassword", "admin", "apassword").get
      }
    }
  }
}
