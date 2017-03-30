package com.convergencelabs.server.datastore.domain

import java.text.SimpleDateFormat
import java.time.Instant

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueExcpetion
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue

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

  val modelPermissions = Some(ModelPermissions(true, true, true, true))
  
  val peopleCollectionId = "people"

  val person1 = ModelFqn(peopleCollectionId, "person1")
  val person1MetaData = ModelMetaData(
    person1,
    20,
    Instant.ofEpochMilli(df.parse("2015-10-20T01:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T12:00:00.000+0000").getTime),
    modelPermissions)
  val person1Data = ObjectValue("0:0", Map("name" -> StringValue("0:1", "person1")))
  val person1Model = Model(person1MetaData, person1Data)

  val person2 = ModelFqn(peopleCollectionId, "person2")
  val person2MetaData = ModelMetaData(
    person2,
    1,
    Instant.ofEpochMilli(df.parse("2015-10-20T02:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T02:00:00.000+0000").getTime),
    modelPermissions)
  val person2Data = ObjectValue("1:0", Map("name" -> StringValue("1:1", "person2")))
  val person2Model = Model(person2MetaData, person2Data)

  val person3 = ModelFqn(peopleCollectionId, "person3")
  val person3MetaData = ModelMetaData(
    person3,
    1,
    Instant.ofEpochMilli(df.parse("2015-10-20T03:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T03:00:00.000+0000").getTime),
    modelPermissions)
  val person3Data = ObjectValue("2:0", Map("name" -> StringValue("2:1", "person3")))
  val person3Model = Model(person3MetaData, person3Data)

  val company1 = ModelFqn("company", "company1")
  val company1MetaData = ModelMetaData(
    company1,
    1,
    Instant.ofEpochMilli(df.parse("2015-10-20T04:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T04:00:00.000+0000").getTime),
    modelPermissions)
  val company1Data = ObjectValue("3:0", Map("name" -> StringValue("3:1", "company")))
  val company1Model = Model(company1MetaData, company1Data)

  val nonExsitingFqn = ModelFqn("notRealCollection", "notRealModel")

  "An ModelStore" when {

    "asked whether a model exists" must {

      "return false if it doesn't exist" in withPersistenceStore { stores =>
        stores.model.modelExists(nonExsitingFqn).get shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        stores.model.createModel(person1Model).get
        stores.model.modelExists(person1).get shouldBe true
      }
    }

    "creating a model" must {
      "create a model that is not a duplicate model fqn" in withPersistenceStore { stores =>
        val modelId = "person4"
        val modelFqn = ModelFqn(peopleCollectionId, modelId)

        val data = ObjectValue(
          "t1-data",
          Map(("foo" -> StringValue("t1-foo", "bar"))))

        stores.collection.ensureCollectionExists(peopleCollectionId)
        stores.model.createModel(peopleCollectionId, Some(modelId), data, modelPermissions).get
        val model = stores.model.getModel(modelFqn).get.value
        model.metaData.fqn shouldBe modelFqn
        model.metaData.version shouldBe 1
        model.data shouldBe data
      }

      "not create a model that is a duplicate model fqn" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        val data = ObjectValue("t2-data",
          Map(("foo" -> StringValue("t2-foo", "bar"))))
        stores.model.createModel(person1.collectionId, Some(person1.modelId), data, modelPermissions).get
        stores.model.createModel(person1.collectionId, Some(person1.modelId), data, modelPermissions).failure.exception shouldBe a[DuplicateValueExcpetion]
      }
    }

    "getting a model" must {
      "return None if it doesn't exist" in withPersistenceStore { stores =>
        stores.model.getModel(nonExsitingFqn).get shouldBe None
      }

      "return Some if it does exist" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        stores.model.createModel(person1Model).get
        stores.model.getModel(person1).get shouldBe defined
      }
    }

    "getting model meta data for a specific model" must {
      "return the correct meta data if it exists" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        stores.model.createModel(person1Model).get
        stores.model.getModelMetaData(person1).get.value shouldBe person1MetaData
      }

      "return None if it does not exist" in withPersistenceStore { stores =>
        stores.model.getModelMetaData(nonExsitingFqn).get shouldBe None
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
        val modelBefore = stores.model.createModel(person1Model).get
        stores.model.updateModelOnOperation(person1Model.metaData.fqn, Instant.now())
        
        val modelAfter = stores.model.getModel(person1Model.metaData.fqn).get.get
        modelAfter.metaData.version shouldBe modelBefore.metaData.version + 1 
      }
      
      "correctly set the timestamp" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        val modelBefore = stores.model.createModel(person1Model).get
        val timeStamp = Instant.now()
        stores.model.updateModelOnOperation(person1Model.metaData.fqn, timeStamp)
        
        val modelAfter = stores.model.getModel(person1Model.metaData.fqn).get.get
        modelAfter.metaData.modifiedTime shouldBe timeStamp 
      }
      
      "leave all other data instact" in withPersistenceStore { stores =>
        stores.collection.ensureCollectionExists(peopleCollectionId)
        val modelBefore = stores.model.createModel(person1Model).get
        stores.model.updateModelOnOperation(person1Model.metaData.fqn, Instant.now())
        
        val modelAfter = stores.model.getModel(person1Model.metaData.fqn).get.get
        modelAfter.metaData.createdTime shouldBe modelBefore.metaData.createdTime
        modelAfter.metaData.fqn shouldBe modelBefore.metaData.fqn
        modelAfter.data shouldBe modelBefore.data
      }
    }

    "querying model data" must {
      "return only models in a single collection" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId", None).get
        list.toSet shouldBe Set(person1Model, person2Model, person3Model)
      }

      "return correct models if a limit is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC LIMIT 2", None).get
        list.toSet shouldBe Set(person1Model, person2Model)
      }

      "return correct models if an offset is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC OFFSET 1", None).get
        list.toSet shouldBe Set(person2Model, person3Model)
      }

      "return correct models if an offset and limit is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC LIMIT 1 OFFSET 1", None).get
        list shouldBe List(person2Model)
      }

      "return models in correct order if orderBy ASC is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC", None).get
        list shouldBe List(person1Model, person2Model, person3Model)
      }

      "return models in correct order if orderBy DESC is provided" in withPersistenceStore { stores =>
        createAllModels(stores)

        val list = stores.model.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name DESC", None).get
        list shouldBe List(person3Model, person2Model, person1Model)
      }
    }

    "deleting a specific model" must {
      "delete the specified model and no others" in withPersistenceStore { stores =>
        createAllModels(stores)

        stores.model.getModel(person1).get shouldBe defined
        stores.model.getModel(person2).get shouldBe defined
        stores.model.getModel(company1).get shouldBe defined

        stores.model.deleteModel(person1).get

        stores.model.getModel(person1).get shouldBe None
        stores.model.getModel(person2).get shouldBe defined
        stores.model.getModel(company1).get shouldBe defined
      }

      "return a failure for deleting a non-existent model" in withPersistenceStore { stores =>
        createAllModels(stores)
        stores.model.getModel(nonExsitingFqn).get shouldBe None
        stores.model.deleteModel(nonExsitingFqn).failure.exception shouldBe a[EntityNotFoundException]
      }
    }

    "deleting all models in collection" must {
      "delete the models in the specified and no others" in withPersistenceStore { stores =>
        createAllModels(stores)

        stores.model.getModel(person1).get shouldBe defined
        stores.model.getModel(person2).get shouldBe defined
        stores.model.getModel(person3).get shouldBe defined
        stores.model.getModel(company1).get shouldBe defined

        stores.model.deleteAllModelsInCollection(person1.collectionId).success

        stores.model.getModel(person1).get shouldBe None
        stores.model.getModel(person2).get shouldBe None
        stores.model.getModel(person3).get shouldBe None
        stores.model.getModel(company1).get shouldBe defined
      }
    }
  }

  def createAllModels(stores: ModelStoreSpecStores): Unit = {
    stores.collection.ensureCollectionExists(company1.collectionId)
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
