package com.convergencelabs.server.datastore.domain

import java.text.SimpleDateFormat
import java.time.Instant

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ModelQueryResult
import com.convergencelabs.server.frontend.rest.DataValueToJValue

case class ModelStoreSpecStores(collection: CollectionStore, model: ModelStore, permissions: ModelPermissionsStore)

// scalastyle:off magic.number
class ModelStoreSpec
    extends PersistenceStoreSpec[ModelStoreSpecStores](DeltaCategory.Domain)
    with WordSpecLike
    with Matchers {

  val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz")

  def createStore(dbProvider: DatabaseProvider): ModelStoreSpecStores = {
    val modelStore = new ModelStore(dbProvider, new ModelOperationStore(dbProvider), new ModelSnapshotStore(dbProvider))
    val collectionStore = new CollectionStore(dbProvider, modelStore)
    val permissionsStore = new ModelPermissionsStore(dbProvider)
    ModelStoreSpecStores(collectionStore, modelStore, permissionsStore)
  }

  val modelPermissions = ModelPermissions(true, true, true, true)

  val peopleCollectionId = "people"

  val person1Id = "person1"
  val person1MetaData = ModelMetaData(
    peopleCollectionId,
    person1Id,
    20,
    Instant.ofEpochMilli(df.parse("2015-10-20T01:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T12:00:00.000+0000").getTime),
    true,
    modelPermissions,
    1)
  val person1Data = ObjectValue("0:0", Map("name" -> StringValue("0:1", "person1")))
  val person1Model = Model(person1MetaData, person1Data)

  val person2Id = "person2"
  val person2MetaData = ModelMetaData(
    peopleCollectionId,
    person2Id,
    1,
    Instant.ofEpochMilli(df.parse("2015-10-20T02:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T02:00:00.000+0000").getTime),
    true,
    modelPermissions,
    1)
  val person2Data = ObjectValue("1:0", Map("name" -> StringValue("1:1", "person2")))
  val person2Model = Model(person2MetaData, person2Data)

  val person3Id = "person3"
  val person3MetaData = ModelMetaData(
    peopleCollectionId,
    person3Id,
    1,
    Instant.ofEpochMilli(df.parse("2015-10-20T03:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T03:00:00.000+0000").getTime),
    true,
    modelPermissions,
    1)
  val person3Data = ObjectValue("2:0", Map("name" -> StringValue("2:1", "person3")))
  val person3Model = Model(person3MetaData, person3Data)

  val companyCollectionId = "company"
  val company1Id = "company1"
  val company1MetaData = ModelMetaData(
    companyCollectionId,
    company1Id,
    1,
    Instant.ofEpochMilli(df.parse("2015-10-20T04:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T04:00:00.000+0000").getTime),
    true,
    modelPermissions,
    1)
  val company1Data = ObjectValue("3:0", Map("name" -> StringValue("3:1", "company")))
  val company1Model = Model(company1MetaData, company1Data)

  val notRealId = "notRealModel"

  "An ModelStore" when {

    "asked whether a model exists" must {

      "return false if it doesn't exist" in withPersistenceStore { stores =>
        stores.model.modelExists(notRealId).get shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        stores.model.createModel(person1Model).get
        stores.model.modelExists(person1Id).get shouldBe true
      }
    }

    "creating a model" must {
      "create a model that is not a duplicate model fqn" in withPersistenceStore { stores =>
        val modelId = "person4"

        val data = ObjectValue(
          "t1-data",
          Map(("foo" -> StringValue("t1-foo", "bar"))))

        stores.collection.ensureCollectionExists(peopleCollectionId)
        stores.model.createModel(modelId, peopleCollectionId, data, true, modelPermissions).get
        val model = stores.model.getModel(modelId).get.value
        model.metaData.modelId shouldBe modelId
        model.metaData.version shouldBe 1
        model.data shouldBe data
      }

      "not create a model that is a duplicate model fqn" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        val data = ObjectValue("t2-data",
          Map(("foo" -> StringValue("t2-foo", "bar"))))
        stores.model.createModel(person1Id, peopleCollectionId, data, true, modelPermissions).get
        stores.model.createModel(person1Id, peopleCollectionId, data, true, modelPermissions).failure.exception shouldBe a[DuplicateValueException]
      }
    }

    "getting a model" must {
      "return None if it doesn't exist" in withPersistenceStore { stores =>
        stores.model.getModel(notRealId).get shouldBe None
      }

      "return Some if it does exist" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        stores.model.createModel(person1Model).get
        stores.model.getModel(person1Id).get shouldBe defined
      }
    }

    "getting model meta data for a specific model" must {
      "return the correct meta data if it exists" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        stores.model.createModel(person1Model).get
        stores.model.getModelMetaData(person1Id).get.value shouldBe person1MetaData
      }

      "return None if it does not exist" in withPersistenceStore { stores =>
        stores.model.getModelMetaData(notRealId).get shouldBe None
      }
    }

    "getting all model meta data for a specific collection" must {
      "return all meta data when no limit or offset are provided" in withPersistenceStore { stores =>
        createAllPersonModels(stores)

        val list = stores.model.getAllModelMetaDataInCollection(peopleCollectionId, None, None).get
        list shouldBe List(
          person1MetaData,
          person2MetaData,
          person3MetaData)
      }

      "return only the limited number of meta data when limit provided" in withPersistenceStore { stores =>
        createAllPersonModels(stores)

        val list = stores.model.getAllModelMetaDataInCollection(peopleCollectionId, None, Some(1)).get
        list shouldBe List(person1MetaData)
      }

      "return only the limited number of meta data when offset provided" in withPersistenceStore { stores =>
        createAllPersonModels(stores)

        val list = stores.model.getAllModelMetaDataInCollection(peopleCollectionId, Some(1), None).get
        list.length shouldBe 2
        list(0) shouldBe person2MetaData
        list(1) shouldBe person3MetaData
      }

      "return only the limited number of meta data when limit and offset provided" in withPersistenceStore { stores =>
        createAllPersonModels(stores)

        val list = stores.model.getAllModelMetaDataInCollection(peopleCollectionId, Some(1), Some(1)).get
        list shouldBe List(person2MetaData)
      }

    }

    "getting all model meta data" must {
      "return all meta data when no limit or offset are provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.getAllModelMetaData(None, None).get
        list shouldBe List(
          company1MetaData,
          person1MetaData,
          person2MetaData,
          person3MetaData)
      }

      "return correct meta data when a limit is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.getAllModelMetaData(None, Some(2)).get
        list shouldBe List(
          company1MetaData,
          person1MetaData)
      }

      "return correct meta data when an offset is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.getAllModelMetaData(Some(2), None).get
        list shouldBe List(
          person2MetaData,
          person3MetaData)
      }

      "return correct meta data when an offset and limit are provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.getAllModelMetaData(Some(1), Some(2)).get
        list shouldBe List(
          person1MetaData,
          person2MetaData)
      }
    }

    "updating a model for an operation" must {
      "increment the version by 1" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        stores.model.createModel(person1Model).get
        stores.model.updateModelOnOperation(person1Id, Instant.now())

        val modelAfter = stores.model.getModel(person1Id).get.get
        modelAfter.metaData.version shouldBe person1Model.metaData.version + 1
      }

      "correctly set the timestamp" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        val modelBefore = stores.model.createModel(person1Model).get
        val timeStamp = Instant.now()
        stores.model.updateModelOnOperation(person1Id, timeStamp)

        val modelAfter = stores.model.getModel(person1Id).get.get
        modelAfter.metaData.modifiedTime shouldBe timeStamp
      }

      "leave all other data instact" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        stores.model.createModel(person1Model).get
        stores.model.updateModelOnOperation(person1Id, Instant.now())

        val modelAfter = stores.model.getModel(person1Id).get.get
        modelAfter.metaData.createdTime shouldBe person1Model.metaData.createdTime
        modelAfter.metaData.modelId shouldBe person1Id
        modelAfter.data shouldBe person1Model.data
      }
    }

    "querying model data" must {
      "return only models in a single collection" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId", None).get
        list.toSet shouldBe Set(
            ModelQueryResult(person1MetaData, DataValueToJValue.toJson(person1Data)), 
            ModelQueryResult(person2MetaData, DataValueToJValue.toJson(person2Data)), 
            ModelQueryResult(person3MetaData, DataValueToJValue.toJson(person3Data)))
      }

      "return correct models if a limit is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC LIMIT 2", None).get
        list.toSet shouldBe Set(
            ModelQueryResult(person1MetaData, DataValueToJValue.toJson(person1Data)), 
            ModelQueryResult(person2MetaData, DataValueToJValue.toJson(person2Data)))
      }

      "return correct models if an offset is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC OFFSET 1", None).get
        list.toSet shouldBe Set(
            ModelQueryResult(person2MetaData, DataValueToJValue.toJson(person2Data)), 
            ModelQueryResult(person3MetaData, DataValueToJValue.toJson(person3Data)))
      }

      "return correct models if an offset and limit is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC LIMIT 1 OFFSET 1", None).get
        list shouldBe List(ModelQueryResult(person2MetaData, DataValueToJValue.toJson(person2Data)))
      }

      "return models in correct order if orderBy ASC is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC", None).get
        list shouldBe List(
            ModelQueryResult(person1MetaData, DataValueToJValue.toJson(person1Data)), 
            ModelQueryResult(person2MetaData, DataValueToJValue.toJson(person2Data)), 
            ModelQueryResult(person3MetaData, DataValueToJValue.toJson(person3Data)))
      }

      "return models in correct order if orderBy DESC is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name DESC", None).get
        list shouldBe List(
            ModelQueryResult(person3MetaData, DataValueToJValue.toJson(person3Data)), 
            ModelQueryResult(person2MetaData, DataValueToJValue.toJson(person2Data)), 
            ModelQueryResult(person1MetaData, DataValueToJValue.toJson(person1Data)))
      }
    }

    "deleting a specific model" must {
      "delete the specified model and no others" in withPersistenceStore { stores =>
        createAllModels(stores)

        stores.model.getModel(person1Id).get shouldBe defined
        stores.model.getModel(person2Id).get shouldBe defined
        stores.model.getModel(company1Id).get shouldBe defined

        stores.model.deleteModel(person1Id).get

        stores.model.getModel(person1Id).get shouldBe None
        stores.model.getModel(person2Id).get shouldBe defined
        stores.model.getModel(company1Id).get shouldBe defined
      }

      "return a failure for deleting a non-existent model" in withPersistenceStore { stores =>
        createAllModels(stores)
        stores.model.getModel(notRealId).get shouldBe None
        stores.model.deleteModel(notRealId).failure.exception shouldBe a[EntityNotFoundException]
      }
    }

    "deleting all models in collection" must {
      "delete the models in the specified and no others" in withPersistenceStore { stores =>
        createAllModels(stores)

        stores.model.getModel(person1Id).get shouldBe defined
        stores.model.getModel(person2Id).get shouldBe defined
        stores.model.getModel(person3Id).get shouldBe defined
        stores.model.getModel(company1Id).get shouldBe defined

        stores.model.deleteAllModelsInCollection(peopleCollectionId).success

        stores.model.getModel(person1Id).get shouldBe None
        stores.model.getModel(person2Id).get shouldBe None
        stores.model.getModel(person3Id).get shouldBe None
        stores.model.getModel(company1Id).get shouldBe defined
      }
    }
  }

  def createAllModels(stores: ModelStoreSpecStores): Unit = {
    stores.collection.ensureCollectionExists(companyCollectionId)
    stores.model.createModel(company1Model).get
    createAllPersonModels(stores)
  }

  def createAllPersonModels(stores: ModelStoreSpecStores): Unit = {
    stores.collection.ensureCollectionExists(peopleCollectionId)
    stores.model.createModel(person1Model).get
    stores.model.createModel(person2Model).get
    stores.model.createModel(person3Model).get
  }
}
