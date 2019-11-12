/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore

import scala.util.Failure
import scala.util.Success
import org.mockito.Mockito
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import com.convergencelabs.server.db.DatabaseProvider
import org.scalactic.source.Position.apply

class AbstractPersistenceProviderSpec extends WordSpec with MockitoSugar with Matchers {
  "A AbstractPersistenceProvider" when {
    "validating the connection" must {
      "return true if a connection can be aquired and closed" in {
        val pool = mock[DatabaseProvider]
        Mockito.when(pool.validateConnection()).thenReturn(Success(()))
        val provider = new AbstractPersistenceProvider(pool) {}
        provider.validateConnection() shouldBe Success(())
      }

      "return false if a connection can not be aquired and closed" in {
        val pool = mock[DatabaseProvider]
        val cause = new IllegalStateException("Induced")
        Mockito.when(pool.validateConnection()).thenReturn(Failure(cause))
        val provider = new AbstractPersistenceProvider(pool) {}
        provider.validateConnection() shouldBe Failure(cause)
      }
    }

    "shutting down" must {
      "close the pool when shutting down the provider" in {
        val pool = mock[DatabaseProvider]
        val provider = new AbstractPersistenceProvider(pool) {}
        provider.shutdown()
        Mockito.verify(pool, Mockito.times(1)).shutdown()
      }
    }
  }
}
