/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedStringSpliceOperation
import com.convergencelabs.convergence.server.backend.services.domain.model.{ModelOperation, NewModelOperation}
import com.convergencelabs.convergence.server.model.domain.model.{Model, ModelMetaData, ModelPermissions, ObjectValue}
import com.convergencelabs.convergence.server.model.domain.session.DomainSession
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserType}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.Instant

// scalastyle:off magic.number
class ModelOperationStoreSpec
  extends DomainPersistenceStoreSpec
    with AnyWordSpecLike
    with Matchers {

  private val testUsername = "test"
  private val user = DomainUser(DomainUserType.Normal, testUsername, None, None, None, None, None)

  private val modelPermissions = ModelPermissions(read = true, write = true, remove = true, manage = true)

  private val peopleCollection = "people"
  private val modelId1 = "person1"
  private val model = Model(
    ModelMetaData(modelId1,
      peopleCollection,
      version = 10,
      truncatedInstantNow(),
      truncatedInstantNow(),
      overridePermissions = true,
      modelPermissions,
      valuePrefix = 1),
    ObjectValue("vid", Map()))

  private val sessionId = "test:1"
  private val session = DomainSession(sessionId, user.toUserId, truncatedInstantNow(), None, "jwt", "js", "1.0", "", "127.0.0.1")

  private val notFoundId = "Exist"

  private val op1 = AppliedStringSpliceOperation("0:0", noOp = false, 1, Some(""), "1")
  private val modelOp1 = NewModelOperation(modelId1, 1L, Instant.ofEpochMilli(10), sessionId, op1)
  private val modelOp1Expected = ModelOperation(modelId1, 1, Instant.ofEpochMilli(10), user.toUserId, sessionId, op1)

  private val op15 = AppliedStringSpliceOperation("0:0", noOp = false, 2, Some(""), "2")
  private val modelOp15 = NewModelOperation(modelId1, 15L, Instant.ofEpochMilli(10), sessionId, op15)

  "A ModelOperationStore" when {
    "creating a ModelOperation" must {
      "store it correctly" in withPersistenceStore { provider =>
        initCommonData(provider)
        provider.modelOperationStore.createModelOperation(modelOp1).get
        provider.modelOperationStore.getModelOperation(modelId1, 1L).success.value.value shouldBe modelOp1Expected
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
        provider.modelOperationStore.getMaxVersion(modelId1).get.get shouldBe 15
      }

      "return None if the model has no operation history" in withPersistenceStore { provider =>
        provider.modelOperationStore.getMaxVersion(notFoundId).success.get shouldBe None
      }
    }

    "requesting version at or before time" must {
      "return the correct version" in withPersistenceStore { provider =>
        initCommonData(provider)
        provider.modelOperationStore.createModelOperation(modelOp1).get
        provider.modelOperationStore.createModelOperation(modelOp15).get
        provider.modelOperationStore.getVersionAtOrBeforeTime(modelId1, truncatedInstantNow()).success.get.get shouldBe 15
      }

      "return None if the model has no operation history" in withPersistenceStore { provider =>
        provider.modelOperationStore.getVersionAtOrBeforeTime(notFoundId, truncatedInstantNow()).success.get shouldBe None
      }
    }

    "requesting the operations after a version" must {
      "return the correct operations" in withPersistenceStore { provider =>
        initCommonData(provider)
        provider.modelOperationStore.createModelOperation(modelOp1).get
        provider.modelOperationStore.createModelOperation(modelOp15).get
        provider.modelOperationStore.getOperationsAfterVersion(modelId1, 6).success.get.size shouldBe 1
      }

      "return the correct operations limited when using a limit" in withPersistenceStore { provider =>
        initCommonData(provider)
        var list = List[ModelOperation]()
        for (version <- 1 to 15) {
          val op = AppliedStringSpliceOperation("0:0", noOp = false, version, Some(""), version.toString)
          val timestamp = truncatedInstantNow()
          val modelOp = NewModelOperation(modelId1, version, timestamp, sessionId, op)
          list = list :+ ModelOperation(modelId1, version, timestamp, user.toUserId, sessionId, op)
          provider.modelOperationStore.createModelOperation(modelOp).get
        }

        list = list.slice(5, 10)

        provider.modelOperationStore.getOperationsAfterVersion(modelId1, 6, Some(5)).success.get shouldBe list
      }

      "return None if the model has no operation history" in withPersistenceStore { provider =>
        provider.modelOperationStore.getOperationsAfterVersion(notFoundId, 6).success.get shouldBe empty
      }
    }
    "deleting all operations for a model" must {
      "remove all operations in the model" in withPersistenceStore { provider =>
        initCommonData(provider)
        provider.modelOperationStore.createModelOperation(modelOp1).get
        provider.modelOperationStore.createModelOperation(modelOp15).get
        provider.modelOperationStore.deleteAllOperationsForModel(modelId1).success
        provider.modelOperationStore.getOperationsAfterVersion(modelId1, 0).success.get shouldBe empty
      }

      "do nothing if model does not exist" in withPersistenceStore { provider =>
        provider.modelOperationStore.deleteAllOperationsForModel(notFoundId).success
      }
    }
  }

  def initCommonData(provider: DomainPersistenceProvider): Unit = {
    provider.userStore.createDomainUser(user).get
    provider.collectionStore.ensureCollectionExists(peopleCollection).get
    provider.modelStore.createModel(model).get
    provider.sessionStore.createSession(session).get
  }
}
