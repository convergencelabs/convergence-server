package com.convergencelabs.server.datastore

import org.scalatest.WordSpec
import org.scalatest.mock.MockitoSugar
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import org.mockito.Mockito
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.Matchers

class AbstractPersistenceProviderSpec extends WordSpec with MockitoSugar with Matchers {
  "A AbstractPersistenceProvider" when {
    "validating the connection" must {
      "return true if a connection can be aquired and closed" in {
        val pool = mock[OPartitionedDatabasePool]
        Mockito.when(pool.acquire()).thenReturn(mock[ODatabaseDocumentTx])
        val provider = new AbstractPersistenceProvider(pool) {}
        provider.validateConnection() shouldBe true
      }

      "return false if a connection can not be aquired and closed" in {
        val pool = mock[OPartitionedDatabasePool]
        Mockito.when(pool.acquire()).thenThrow(new IllegalStateException())
        val provider = new AbstractPersistenceProvider(pool) {}
        provider.validateConnection() shouldBe false
      }
    }

    "shutting down" must {
      "close the pool when shutting down the provider" in {
        val pool = mock[OPartitionedDatabasePool]
        val provider = new AbstractPersistenceProvider(pool) {}
        provider.shutdown()
        Mockito.verify(pool, Mockito.times(1)).close()
      }
    }
  }
}
