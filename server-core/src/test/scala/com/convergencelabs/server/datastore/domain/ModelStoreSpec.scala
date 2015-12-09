package com.convergencelabs.server.datastore.domain

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

class ModelStoreSpec
    extends PersistenceStoreSpec[ModelStore]("/dbfiles/domain.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): ModelStore = new ModelStore(dbPool)

  val exisitngFqn = ModelFqn("people", "person1")
  val nonExsitingFqn = ModelFqn("notRealCollection", "notRealModel")

  "An ModelStore" when {

    "asked whether a model exists" must {

      "return false if it doesn't exist" in withPersistenceStore { store =>
        store.modelExists(nonExsitingFqn).success.value shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { store =>
        store.modelExists(exisitngFqn).success.value shouldBe true
      }
    }
    "retrieving model data" must {
      "return None if it doesn't exist" in withPersistenceStore { store =>
        store.getModel(nonExsitingFqn).success.value shouldBe None
      }

      "return Some if it does exist" in withPersistenceStore { store =>
        store.getModel(exisitngFqn).success.value shouldBe defined
      }
    }
  }
}
