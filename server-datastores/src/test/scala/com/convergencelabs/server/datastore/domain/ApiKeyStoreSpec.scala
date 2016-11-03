package com.convergencelabs.server.datastore.domain

import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.TokenKeyPair
import com.convergencelabs.server.domain.TokenPublicKey
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

// scalastyle:off line.size.limit
class ApiKeyStoreSpec
    extends PersistenceStoreSpec[ApiKeyStore]("/dbfiles/domain-n1-d1.json.gz")
    with WordSpecLike
    with Matchers {

  val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz")

  val tokenKey = TokenPublicKey(
    "test",
    "A key for testing",
    Instant.ofEpochMilli(df.parse("2015-09-21T12:32:43.000+0000").getTime),
    "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlqHvXYPseLVx2vCSCipw\nxOumeB2t3JFxXnQ1qC9QMcAydKT7oeTZEbJxgbpaeEy2QkH7J8drkfu78RBh9nFb\nJ3yh3/cZS09wMM4NQsmqqpee4qHromwR1N4MgYAzDGcnl0iHeaYc56tHt5n5ENjG\n+bjEULEGIFVbDs2MBxyswchyqRFGHse41uLxhybhDNkEQLUcqkbVesqVXGsz25Hi\nBdxfYRJ9+2x70GwxDf0nBfZb1fvUFZnCDJHKO/wqv5k9BcuCDLUOdRgJa7+E721V\nSvopddtRgEFnEWoOilcUWtF03CUy4tw3hv1VDcbXKsEma0KjP5IAvWFVI6dbDHeh\nLQIDAQAB\n-----END PUBLIC KEY-----\n",
    true)

  val keys = List(tokenKey)

  def createStore(dbPool: OPartitionedDatabasePool): ApiKeyStore = new ApiKeyStore(dbPool)

  "A ApiKeyStore" when {
    "retrieving domain keys" must {
      "return the correct list of all keys" in withPersistenceStore { store =>
        store.getKeys(None, None).success.get shouldBe keys
      }

      "return the correct key by id" in withPersistenceStore { store =>
        store.getKey(tokenKey.id).success.value.get shouldBe tokenKey
      }
    }
  }
}
