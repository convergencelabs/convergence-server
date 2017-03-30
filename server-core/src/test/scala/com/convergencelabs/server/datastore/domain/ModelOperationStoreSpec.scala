package com.convergencelabs.server.datastore.domain

import java.time.Instant

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.NewModelOperation
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation

// scalastyle:off magic.number
class ModelOperationStoreSpec
    extends PersistenceStoreSpec[DomainPersistenceProvider](DeltaCategory.Domain)
    with WordSpecLike
    with Matchers {

  def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider = new DomainPersistenceProvider(dbProvider)

  val testUsername = "test"
  val user = DomainUser(DomainUserType.Normal, testUsername, None, None, None, None)

  val modelPermissions = Some(ModelPermissions(true, true, true, true))
  
  val modelFqn = ModelFqn("people", "person1")
  val model = Model(
    ModelMetaData(modelFqn, 10L, Instant.now(), Instant.now(), modelPermissions),
    ObjectValue("vid", Map()))

  val sessionId = "test:1"
  val session = DomainSession(sessionId, testUsername, Instant.now(), None, "jwt", "js", "1.0", "", "127.0.0.1")

  val notFoundFqn = ModelFqn("Does Not", "Exist")

  val op1 = AppliedStringInsertOperation("0:0", false, 1, "1")
  val modelOp1 = NewModelOperation(modelFqn, 1L, Instant.ofEpochMilli(10), sessionId, op1)
  val modelOp1Expected = ModelOperation(modelFqn, 1L, Instant.ofEpochMilli(10), testUsername, sessionId, op1)

  val op15 = AppliedStringInsertOperation("0:0", false, 2, "2")
  val modelOp15 = NewModelOperation(modelFqn, 15L, Instant.ofEpochMilli(10), sessionId, op15)
  val modelOp15Expected = ModelOperation(modelFqn, 15L, Instant.ofEpochMilli(10), testUsername, sessionId, op15)

  "A ModelOperationStore" when {
    "creating a ModelOperation" must {
      "store it correctly" in withPersistenceStore { provider =>
        initCommonData(provider)
        provider.modelOperationStore.createModelOperation(modelOp1).get
        provider.modelOperationStore.getModelOperation(modelFqn, 1L).success.value.value shouldBe modelOp1Expected
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
          val timestamp = Instant.now()
          val modelOp = NewModelOperation(modelFqn, version, timestamp, sessionId, op)
          list = list :+ ModelOperation(modelFqn, version, timestamp, testUsername, sessionId, op) 
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
    provider.userStore.createDomainUser(user).get
    provider.collectionStore.ensureCollectionExists(modelFqn.collectionId).get
    provider.modelStore.createModel(model).get
    provider.sessionStore.createSession(session).get
  }
}
