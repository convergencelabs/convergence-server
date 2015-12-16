package com.convergencelabs.server.datastore.domain

import org.scalatest.WordSpec
import org.scalatest.mock.MockitoSugar
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import org.mockito.Mockito
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.Matchers

class DomainPersistenceProviderSpec extends WordSpec with MockitoSugar with Matchers {
  "A DomainPersistenceProvider" when {
    "validating the connection" must {
      "return true if a connection can be aquired and clsoed" in {
        val pool = mock[OPartitionedDatabasePool]
        Mockito.when(pool.acquire()).thenReturn(mock[ODatabaseDocumentTx])
        val provider = new DomainPersistenceProvider(pool)
        provider.validateConnection() shouldBe true
      }
      
      "return false if a connection can not be aquired and clsoed" in {
        val pool = mock[OPartitionedDatabasePool]
        Mockito.when(pool.acquire()).thenThrow(new IllegalStateException())
        val provider = new DomainPersistenceProvider(pool)
        provider.validateConnection() shouldBe false
      }
    }
  }
}