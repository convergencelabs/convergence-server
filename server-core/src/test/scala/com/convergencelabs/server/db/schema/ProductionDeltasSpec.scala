package com.convergencelabs.server.schema

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.TryValues._
import com.convergencelabs.server.db.schema.DeltaManager
import com.convergencelabs.server.db.schema.DeltaCategory

class ProductionDeltasSpec extends WordSpecLike with Matchers {

  "Production Deltas" when {
    
    "processing Convergence Deltas" must {
      "validate" in {
        val manager = new DeltaManager(None)
        val manifest = manager.manifest(DeltaCategory.Convergence).success.value
        manifest.validate()
      }
    }
    
    "processing Domain Deltas" must {
      "validate" in {
        val manager = new DeltaManager(None)
        val manifest = manager.manifest(DeltaCategory.Domain).success.value
        manifest.validate()
      }
    }
    
    "processing Version Deltas" must {
      "validate" in {
        val manager = new DeltaManager(None)
        val manifest = manager.manifest(DeltaCategory.Version).success.value
        manifest.validate()
      }
    }
  }
}