package com.convergencelabs.server.datastore.domain

import java.text.SimpleDateFormat
import java.time.Instant

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.JwtAuthKey

// scalastyle:off line.size.limit
class JwtAuthKeyStoreSpec
    extends PersistenceStoreSpec[JwtAuthKeyStore](DeltaCategory.Domain)
    with WordSpecLike
    with Matchers {

  val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz")

  val jwtAuthKey = JwtAuthKey(
    "test",
    "A key for testing",
    Instant.ofEpochMilli(df.parse("2015-09-21T12:32:43.000+0000").getTime),
    "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlqHvXYPseLVx2vCSCipw\nxOumeB2t3JFxXnQ1qC9QMcAydKT7oeTZEbJxgbpaeEy2QkH7J8drkfu78RBh9nFb\nJ3yh3/cZS09wMM4NQsmqqpee4qHromwR1N4MgYAzDGcnl0iHeaYc56tHt5n5ENjG\n+bjEULEGIFVbDs2MBxyswchyqRFGHse41uLxhybhDNkEQLUcqkbVesqVXGsz25Hi\nBdxfYRJ9+2x70GwxDf0nBfZb1fvUFZnCDJHKO/wqv5k9BcuCDLUOdRgJa7+E721V\nSvopddtRgEFnEWoOilcUWtF03CUy4tw3hv1VDcbXKsEma0KjP5IAvWFVI6dbDHeh\nLQIDAQAB\n-----END PUBLIC KEY-----\n",
    true)


  def createStore(dbProvider: DatabaseProvider): JwtAuthKeyStore = new JwtAuthKeyStore(dbProvider)

  "A ApiKeyStore" when {
    "retrieving domain keys" must {
      "return the correct list of all keys" in withPersistenceStore { store =>
        store.importKey(jwtAuthKey)
        store.getKeys(None, None).success.get shouldBe List(jwtAuthKey)
      }

      "return the correct key by id" in withPersistenceStore { store =>
        store.importKey(jwtAuthKey)
        store.getKey(jwtAuthKey.id).success.value.get shouldBe jwtAuthKey
      }
    }
  }
}