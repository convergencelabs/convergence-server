package com.convergencelabs.server.datastore.domain

import java.time.Instant
import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JBool
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JNothing
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.jvalue2monadic
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import org.json4s.JsonAST.JDouble
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.datastore.DuplicateValueExcpetion
import com.convergencelabs.server.domain.model.Collection
import com.convergencelabs.server.datastore.EntityNotFoundException

class ModelPermissionsStoreSpec
    extends PersistenceStoreSpec[DomainPersistenceProvider](DeltaCategory.Domain)
    with WordSpecLike
    with Matchers {

  val modelPermissions = ModelPermissions(true, true, true, true)
  
  val collection1 = "collection1"
  val model1 = "model1"
  val model2 = "model2"
  val nonExistentCollectionId = "not_real"
  val modelFqn = ModelFqn(collection1, model1)
  val nonRealId = "not_real"
  val nonExistentModelFqn = ModelFqn("test", nonRealId)

  def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider = new DomainPersistenceProvider(dbProvider)

  "A ModelPermissionsStore" when {
    "retrieving the model world permissions" must {
      "be equal to those just set" in withTestData { provider =>
        val permissions = ModelPermissions(true, false, true, false)
        provider.modelPermissionsStore.setModelWorldPermissions(model1, permissions).get
        val retrievedPermissions = provider.modelPermissionsStore.getModelWorldPermissions(model1).get
        retrievedPermissions shouldEqual permissions
      }

      "fail if model does not exist" in withTestData { provider =>
        an[IllegalStateException] should be thrownBy provider.modelPermissionsStore.getModelWorldPermissions(nonRealId).get 
        }
    }

    "retrieving the model user permissions" must {
      "be equal to those just set" in withTestData { provider =>
        val permissions = ModelPermissions(true, false, true, false)
        provider.modelPermissionsStore.updateModelUserPermissions(model1, model1, permissions).get
        val retrievedPermissions = provider.modelPermissionsStore.getModelUserPermissions(model1, model1).get
        retrievedPermissions shouldEqual Some(permissions)
      }

      "be none if no permissions are set" in withTestData { provider =>
        val retrievedPermissions = provider.modelPermissionsStore.getModelUserPermissions(model1, model1).get
        retrievedPermissions shouldEqual None
      }
    }

    "retrieving all model user permissions" must {
      "contain all those just set" in withTestData { provider =>
        val permissions = ModelPermissions(true, false, true, false)
        provider.modelPermissionsStore.updateModelUserPermissions(model1, model1, permissions).get
        provider.modelPermissionsStore.updateModelUserPermissions(model1, model2, permissions).get
        val retrievedPermissions = provider.modelPermissionsStore.getAllModelUserPermissions(model1).get
        retrievedPermissions shouldEqual Map(model1 -> permissions, model2 -> permissions)
      }

      "fail if model does not exist" in withTestData { provider =>
        an[IllegalStateException] should be thrownBy provider.modelPermissionsStore.getAllModelUserPermissions(nonRealId).get
      }
    }

    "deleting a model user permissions" must {
      "must no longer be set on the model" in withTestData { provider =>
        val permissions = ModelPermissions(true, false, true, false)
        provider.modelPermissionsStore.updateModelUserPermissions(model1, model1, permissions).get
        provider.modelPermissionsStore.updateModelUserPermissions(model1, model2, permissions).get
        provider.modelPermissionsStore.removeModelUserPermissions(model1, model1)
        val retrievedPermissions = provider.modelPermissionsStore.getAllModelUserPermissions(model1).get
        retrievedPermissions shouldEqual Map(model2 -> permissions)
      }
    }
    
    "retrieving the collection world permissions" must {
      "be equal to those just set" in withTestData { provider =>
        val permissions = CollectionPermissions(false, true, false, true, false)
        provider.modelPermissionsStore.setCollectionWorldPermissions(collection1, permissions).get
        val retrievedPermissions = provider.modelPermissionsStore.getCollectionWorldPermissions(collection1).get
        retrievedPermissions shouldEqual permissions
      }

      "fail if collection does not exist" in withTestData { provider =>
        provider.modelPermissionsStore.getCollectionWorldPermissions(nonExistentCollectionId).failure
      }
    }

    "retrieving the collection user permissions" must {
      "be equal to those just set" in withTestData { provider =>
        val permissions = CollectionPermissions(false, true, false, true, false)
        provider.modelPermissionsStore.updateCollectionUserPermissions(collection1, model1, permissions).get
        val retrievedPermissions = provider.modelPermissionsStore.getCollectionUserPermissions(collection1, model1).get
        retrievedPermissions shouldEqual Some(permissions)
      }

      "be none if no permissions are set" in withTestData { provider =>
        val retrievedPermissions = provider.modelPermissionsStore.getCollectionUserPermissions(collection1, model1).get
        retrievedPermissions shouldEqual None
      }
    }

    "retrieving all collection user permissions" must {
      "contain all those just set" in withTestData { provider =>
        val permissions = CollectionPermissions(false, true, false, true, false)
        provider.modelPermissionsStore.updateCollectionUserPermissions(collection1, model1, permissions).get
        provider.modelPermissionsStore.updateCollectionUserPermissions(collection1, model2, permissions).get
        val retrievedPermissions = provider.modelPermissionsStore.getAllCollectionUserPermissions(collection1).get
        retrievedPermissions shouldEqual Map(model1 -> permissions, model2 -> permissions)
      }

      "fail if collection does not exist" in withTestData { provider =>
        an[EntityNotFoundException] should be thrownBy provider.modelPermissionsStore.getAllCollectionUserPermissions(nonExistentCollectionId).get
      }
    }

    "deleting a collection user permissions" must {
      "must no longer be set on the collection" in withTestData { provider =>
        val permissions = CollectionPermissions(false, true, false, true, false)
        provider.modelPermissionsStore.updateCollectionUserPermissions(collection1, model1, permissions).get
        provider.modelPermissionsStore.updateCollectionUserPermissions(collection1, model2, permissions).get
        provider.modelPermissionsStore.removeCollectionUserPermissions(collection1, model1)
        val retrievedPermissions = provider.modelPermissionsStore.getAllCollectionUserPermissions(collection1).get
        retrievedPermissions shouldEqual Map(model2 -> permissions)
      }
    }
  }

  def withTestData(testCode: DomainPersistenceProvider => Any): Unit = {
    this.withPersistenceStore { provider =>
      provider.collectionStore.ensureCollectionExists(collection1).get 
      provider.modelStore.createModel(collection1, Some(model1), ObjectValue("vid", Map()), true, modelPermissions).get
      provider.modelStore.createModel(collection1, Some(model2), ObjectValue("vid", Map()), true, modelPermissions).get
      provider.userStore.createDomainUser(DomainUser(DomainUserType.Normal, model1, None, None, None, None)).get
      provider.userStore.createDomainUser(DomainUser(DomainUserType.Normal, model2, None, None, None, None)).get
      testCode(provider)
    }
  }
}
