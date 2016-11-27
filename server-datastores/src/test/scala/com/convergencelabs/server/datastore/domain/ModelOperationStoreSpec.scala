package com.convergencelabs.server.datastore.domain

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.OptionValues._
import org.scalatest.WordSpecLike
import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import java.time.Instant
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.Model

// scalastyle:off magic.number
class ModelOperationStoreSpec
    extends PersistenceStoreSpec[DomainPersistenceProvider]("/domain.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): DomainPersistenceProvider = new DomainPersistenceProvider(dbPool)

  val testUsername = "test"
  val user = DomainUser(DomainUserType.Normal, testUsername, None, None, None, None)

  val modelFqn = ModelFqn("people", "person1")
  val model = Model(
    ModelMetaData(modelFqn, 10L, Instant.now(), Instant.now()),
    ObjectValue("vid", Map()))

  val sessionId = "test:1"

  val notFoundFqn = ModelFqn("Does Not", "Exist")

  val op1 = AppliedStringInsertOperation("0:0", false, 1, "1")
  val modelOp1 = ModelOperation(modelFqn, 1L, Instant.ofEpochMilli(10), testUsername, sessionId, op1)

  val op15 = AppliedStringInsertOperation("0:0", false, 2, "2")
  val modelOp15 = ModelOperation(modelFqn, 15L, Instant.ofEpochMilli(10), testUsername, sessionId, op15)

  "A ModelOperationStore" when {
    "creating a ModelOperation" must {
      "store it correctly" in withPersistenceStore { provider =>
        initCommonData(provider)
        provider.modelOperationStore.createModelOperation(modelOp1).get
        provider.modelOperationStore.getModelOperation(modelFqn, 1L).success.value.value shouldBe modelOp1
      }

      "disallow duplicates" in withPersistenceStore { provider =>
        initCommonData(provider)
        provider.modelOperationStore.createModelOperation(modelOp1).get
        provider.modelOperationStore.createModelOperation(modelOp1).failure
      }
    }

    "requesting max version" must {
      "return the version number of the last operation" in withPersistenceStore { provider =>
        initCommonData(provider)
        provider.modelOperationStore.createModelOperation(modelOp1).get
        provider.modelOperationStore.createModelOperation(modelOp15).get
        provider.modelOperationStore.getMaxVersion(modelFqn).success.get.get shouldBe 15
      }

      "return None if the model has no operation history" in withPersistenceStore { provider =>
        provider.modelOperationStore.getMaxVersion(notFoundFqn).success.get shouldBe None
      }
    }

    "requesting version at or before time" must {
      "return the correct version" in withPersistenceStore { provider =>
        initCommonData(provider)
        provider.modelOperationStore.createModelOperation(modelOp1).get
        provider.modelOperationStore.createModelOperation(modelOp15).get
        provider.modelOperationStore.getVersionAtOrBeforeTime(modelFqn, Instant.now()).success.get.get shouldBe 15
      }

      "return None if the model has no operation history" in withPersistenceStore { provider =>
        provider.modelOperationStore.getVersionAtOrBeforeTime(notFoundFqn, Instant.now()).success.get shouldBe None
      }
    }

    "requestiong the operations after a version" must {
      "return the correct operations" in withPersistenceStore { provider =>
        initCommonData(provider)
        provider.modelOperationStore.createModelOperation(modelOp1).get
        provider.modelOperationStore.createModelOperation(modelOp15).get
        provider.modelOperationStore.getOperationsAfterVersion(modelFqn, 6).success.get.size shouldBe 1
      }

      "return the correct operations limited when using a limit" in withPersistenceStore { provider =>
        initCommonData(provider)
        var list = List[ModelOperation]()
        for (version <- 1 to 15) {
          val op = AppliedStringInsertOperation("0:0", false, version, version.toString)
          val modelOp = ModelOperation(modelFqn, version, Instant.now(), testUsername, sessionId, op)
          list = list :+ modelOp 
          provider.modelOperationStore.createModelOperation(modelOp).get
        }
        
        list = list.slice(5, 10)

        provider.modelOperationStore.getOperationsAfterVersion(modelFqn, 6, 5).success.get shouldBe list
      }

      "return None if the model has no operation history" in withPersistenceStore { provider =>
        provider.modelOperationStore.getOperationsAfterVersion(notFoundFqn, 6).success.get shouldBe empty
      }
    }
    "deleting all operations for a model" must {
      "remove all operations in the model" in withPersistenceStore { provider =>
        initCommonData(provider)
        provider.modelOperationStore.createModelOperation(modelOp1).get
        provider.modelOperationStore.createModelOperation(modelOp15).get
        provider.modelOperationStore.deleteAllOperationsForModel(modelFqn).success
        provider.modelOperationStore.getOperationsAfterVersion(modelFqn, 0).success.get shouldBe empty
      }

      "do nothing if model does not exist" in withPersistenceStore { provider =>
        provider.modelOperationStore.deleteAllOperationsForModel(notFoundFqn).success
      }
    }
  }

  def initCommonData(provider: DomainPersistenceProvider): Unit = {
    provider.userStore.createDomainUser(user, None).get
    provider.collectionStore.ensureCollectionExists(modelFqn.collectionId).get
    provider.modelStore.createModel(model).get
  }
}
