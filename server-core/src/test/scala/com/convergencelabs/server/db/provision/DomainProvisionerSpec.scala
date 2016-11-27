package com.convergencelabs.server.db.provision

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpec
import com.convergencelabs.server.util.EmbeddedOrientDB

class DomainProvisionerSpec()
    extends WordSpec
    with Matchers
    with BeforeAndAfterAll {

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
        val provisioner = new DomainProvisioner("remote:localhost", "root", "password", true)
        provisioner.provisionDomain("DomainProvisionerTest", "writer", "wpassword", "admin", "apassword").get
      }
    }
  }
}
