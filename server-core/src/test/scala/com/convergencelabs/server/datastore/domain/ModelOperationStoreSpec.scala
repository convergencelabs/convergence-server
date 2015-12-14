package com.convergencelabs.server.datastore.domain

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import java.time.Instant

class ModelOperationStoreSpec
    extends PersistenceStoreSpec[ModelOperationStore]("/dbfiles/domain.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): ModelOperationStore = new ModelOperationStore(dbPool)

  val modelFqn = ModelFqn("people", "person1")
  val notFoundFqn = ModelFqn("Does Not", "Exist")

  "A ModelOperationStore" when {
    "requesting max version" must {
      "return the version number of the last operation" in withPersistenceStore { store =>
        store.getMaxVersion(modelFqn).success.get.get shouldBe 15
      }

      "return None if the model has no operation history" in withPersistenceStore { store =>
        store.getMaxVersion(notFoundFqn).success.get shouldBe None
      }
    }

    "requesting version at or before time" must {
      "return the correct version" in withPersistenceStore { store =>
        store.getVersionAtOrBeforeTime(modelFqn, Instant.now()).success.get.get shouldBe 15
      }

      "return None if the model has no operation history" in withPersistenceStore { store =>
        store.getVersionAtOrBeforeTime(notFoundFqn, Instant.now()).success.get shouldBe None
      }
    }

    "requestiong the operations after a version" must {
      "return the correct operations" in withPersistenceStore { store =>
        store.getOperationsAfterVersion(modelFqn, 6).success.get.size shouldBe 10
      }

      "return the correct operations limited when using a limit" in withPersistenceStore { store =>
        store.getOperationsAfterVersion(modelFqn, 6, 5).success.get.size shouldBe 5
      }

      "return None if the model has no operation history" in withPersistenceStore { store =>
        store.getOperationsAfterVersion(notFoundFqn, 6).success.get shouldBe empty
      }
    }
    "deleting all operations for a model" must {
      "remove all operations in the model" in withPersistenceStore { store =>
        store.removeOperationsForModel(modelFqn).success
        store.getOperationsAfterVersion(modelFqn, 0).success.get shouldBe empty
      }

      "do nothing if model does not exist" in withPersistenceStore { store =>
        store.removeOperationsForModel(notFoundFqn).success
      }
    }
  }
}
