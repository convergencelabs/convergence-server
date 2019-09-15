package com.convergencelabs.server.db.schema

import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure

import org.scalatest.WordSpec

class ProductionDeltasSpec extends WordSpec with Matchers {

  "Production Deltas" when {
    "processing Convergence Deltas" must {
      "validateIndex" in {
        val manager = new DeltaManager(None)
        val manifest = manager.manifest(DeltaCategory.Convergence).success.value
        manifest.validateIndex()
      }
    }
    
    "processing Domain Deltas" must {
      "validateIndex" in {
        val manager = new DeltaManager(None)
        val manifest = manager.manifest(DeltaCategory.Domain).success.value
        manifest.validateIndex()
      }
    }
  }
}
