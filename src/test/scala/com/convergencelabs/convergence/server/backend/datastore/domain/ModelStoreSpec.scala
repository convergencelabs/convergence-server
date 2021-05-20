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

import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.backend.services.domain.model.ModelQueryResult
import com.convergencelabs.convergence.server.model.domain.model
import com.convergencelabs.convergence.server.model.domain.model._
import com.convergencelabs.convergence.server.util.{DataValueToJValue, QueryLimit, QueryOffset}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.text.SimpleDateFormat
import java.time.Instant

// scalastyle:off magic.number
class ModelStoreSpec
  extends DomainPersistenceStoreSpec
  with AnyWordSpecLike
  with Matchers {

  val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz")

  private val modelPermissions = ModelPermissions(read = true, write = true, remove = true, manage = true)

  private val peopleCollectionId = "people"

  private val person1Id = "person1"
  private val person1MetaData = ModelMetaData(
    person1Id,
    peopleCollectionId,
    20,
    Instant.ofEpochMilli(df.parse("2015-10-20T01:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T12:00:00.000+0000").getTime),
    overridePermissions = true,
    modelPermissions,
    1)
  private val person1Data = ObjectValue("0:0", Map("name" -> StringValue("0:1", "person1")))
  private val person1Model = Model(person1MetaData, person1Data)

  private val person2Id = "person2"
  private val person2MetaData = model.ModelMetaData(
    person2Id,
    peopleCollectionId,
    1,
    Instant.ofEpochMilli(df.parse("2015-10-20T02:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T02:00:00.000+0000").getTime),
    overridePermissions = true,
    modelPermissions,
    1)
  private val person2Data = ObjectValue("1:0", Map("name" -> StringValue("1:1", "person2")))
  private val person2Model = model.Model(person2MetaData, person2Data)

  private val person3Id = "person3"
  private val person3MetaData = model.ModelMetaData(
    person3Id,
    peopleCollectionId,
    1,
    Instant.ofEpochMilli(df.parse("2015-10-20T03:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T03:00:00.000+0000").getTime),
    overridePermissions = true,
    modelPermissions,
    1)
  private val person3Data = ObjectValue("2:0", Map("name" -> StringValue("2:1", "person3")))
  private val person3Model = model.Model(person3MetaData, person3Data)

  private val companyCollectionId = "company"
  private val company1Id = "company1"
  private val company1MetaData = model.ModelMetaData(
    company1Id,
    companyCollectionId,
    1,
    Instant.ofEpochMilli(df.parse("2015-10-20T04:00:00.000+0000").getTime),
    Instant.ofEpochMilli(df.parse("2015-10-20T04:00:00.000+0000").getTime),
    overridePermissions = true,
    modelPermissions,
    1)
  private val company1Data = ObjectValue("3:0", Map("name" -> StringValue("3:1", "company")))
  private val company1Model = model.Model(company1MetaData, company1Data)

  private val notRealId = "notRealModel"

  "An ModelStore" when {

    "asked whether a model exists" must {

      "return false if it doesn't exist" in withPersistenceStore { provider =>
        provider.modelStore.modelExists(notRealId).get shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { provider =>
        provider.collectionStore.ensureCollectionExists(peopleCollectionId)
        provider.modelStore.createModel(person1Model).get
        provider.modelStore.modelExists(person1Id).get shouldBe true
      }
    }

    "creating a model" must {
      "create a model that is not a duplicate model fqn" in withPersistenceStore { provider =>
        val modelId = "person4"

        val data = ObjectValue(
          "t1-data",
          Map("foo" -> StringValue("t1-foo", "bar")))

        provider.collectionStore.ensureCollectionExists(peopleCollectionId)
        provider.modelStore.createModel(modelId, peopleCollectionId, data, None, overridePermissions = true, modelPermissions).get
        val model = provider.modelStore.getModel(modelId).get.value
        model.metaData.id shouldBe modelId
        model.metaData.version shouldBe 1
        model.data shouldBe data
      }

      "not create a model that is a duplicate model fqn" in withPersistenceStore { provider =>
        provider.collectionStore.ensureCollectionExists(peopleCollectionId)
        val data = ObjectValue(
          "t2-data",
          Map("foo" -> StringValue("t2-foo", "bar")))
        provider.modelStore.createModel(person1Id, peopleCollectionId, data, None, overridePermissions = true, modelPermissions).get
        provider.modelStore.createModel(person1Id, peopleCollectionId, data, None, overridePermissions = true, modelPermissions).failure.exception shouldBe a[DuplicateValueException]
      }
    }

    "getting a model" must {
      "return None if it doesn't exist" in withPersistenceStore { provider =>
        provider.modelStore.getModel(notRealId).get shouldBe None
      }

      "return Some if it does exist" in withPersistenceStore { provider =>
        provider.collectionStore.ensureCollectionExists(peopleCollectionId)
        provider.modelStore.createModel(person1Model).get
        provider.modelStore.getModel(person1Id).get shouldBe defined
      }
    }

    "getting model meta data for a specific model" must {
      "return the correct meta data if it exists" in withPersistenceStore { provider =>
        provider.collectionStore.ensureCollectionExists(peopleCollectionId)
        provider.modelStore.createModel(person1Model).get
        provider.modelStore.getModelMetaData(person1Id).get.value shouldBe person1MetaData
      }

      "return None if it does not exist" in withPersistenceStore { provider =>
        provider.modelStore.getModelMetaData(notRealId).get shouldBe None
      }
    }

    "getting all model meta data for a specific collection" must {
      "return all meta data when no limit or offset are provided" in withPersistenceStore { provider =>
        createAllPersonModels(provider)

        val list = provider.modelStore.getAllModelMetaDataInCollection(peopleCollectionId, QueryOffset(), QueryLimit()).get
        list shouldBe List(
          person1MetaData,
          person2MetaData,
          person3MetaData)
      }

      "return only the limited number of meta data when limit provided" in withPersistenceStore { provider =>
        createAllPersonModels(provider)

        val list = provider.modelStore.getAllModelMetaDataInCollection(peopleCollectionId, QueryOffset(), QueryLimit(1)).get
        list shouldBe List(person1MetaData)
      }

      "return only the the correct number of meta data when an offset is provided" in withPersistenceStore { provider =>
        createAllPersonModels(provider)

        val list = provider.modelStore.getAllModelMetaDataInCollection(peopleCollectionId, QueryOffset(1), QueryLimit()).get
        list.length shouldBe 2
        list.head shouldBe person2MetaData
        list(1) shouldBe person3MetaData
      }

      "return only the limited number of meta data when limit and offset provided" in withPersistenceStore { provider =>
        createAllPersonModels(provider)

        val list = provider.modelStore.getAllModelMetaDataInCollection(peopleCollectionId, QueryOffset(1), QueryLimit(1)).get
        list shouldBe List(person2MetaData)
      }

    }

    "getting all model meta data" must {
      "return all meta data when no limit or offset are provided" in withPersistenceStore { provider =>
        createAllModels(provider)

        val list = provider.modelStore.getAllModelMetaData(QueryOffset(), QueryLimit()).get
        list shouldBe List(
          company1MetaData,
          person1MetaData,
          person2MetaData,
          person3MetaData)
      }

      "return correct meta data when a limit is provided" in withPersistenceStore { provider =>
        createAllModels(provider)

        val list = provider.modelStore.getAllModelMetaData(QueryOffset(), QueryLimit(2)).get
        list shouldBe List(
          company1MetaData,
          person1MetaData)
      }

      "return correct meta data when an offset is provided" in withPersistenceStore { provider =>
        createAllModels(provider)

        val list = provider.modelStore.getAllModelMetaData(QueryOffset(2), QueryLimit()).get
        list shouldBe List(
          person2MetaData,
          person3MetaData)
      }

      "return correct meta data when an offset and limit are provided" in withPersistenceStore { provider =>
        createAllModels(provider)

        val list = provider.modelStore.getAllModelMetaData(QueryOffset(1), QueryLimit(2)).get
        list shouldBe List(
          person1MetaData,
          person2MetaData)
      }
    }

    "updating a model for an operation" must {
      "increment the version by 1" in withPersistenceStore { provider =>
        provider.collectionStore.ensureCollectionExists(peopleCollectionId)
        provider.modelStore.createModel(person1Model).get
        provider.modelStore.updateModelOnOperation(person1Id, person1Model.metaData.version + 1, Instant.now())

        val modelAfter = provider.modelStore.getModel(person1Id).get.get
        modelAfter.metaData.version shouldBe person1Model.metaData.version + 1
      }

      "correctly set the timestamp" in withPersistenceStore { provider =>
        provider.collectionStore.ensureCollectionExists(peopleCollectionId)
        provider.modelStore.createModel(person1Model).get
        val timeStamp = Instant.now()
        provider.modelStore.updateModelOnOperation(person1Id, person1Model.metaData.version + 1, timeStamp)

        val modelAfter = provider.modelStore.getModel(person1Id).get.get
        modelAfter.metaData.modifiedTime.toEpochMilli shouldBe timeStamp.toEpochMilli
      }

      "leave all other data intact" in withPersistenceStore { provider =>
        provider.collectionStore.ensureCollectionExists(peopleCollectionId)
        provider.modelStore.createModel(person1Model).get
        provider.modelStore.updateModelOnOperation(person1Id, person1Model.metaData.version + 1, Instant.now())

        val modelAfter = provider.modelStore.getModel(person1Id).get.get
        modelAfter.metaData.createdTime shouldBe person1Model.metaData.createdTime
        modelAfter.metaData.id shouldBe person1Id
        modelAfter.data shouldBe person1Model.data
      }
    }

    "querying model data" must {
      "return only models in a single collection" in withPersistenceStore { provider =>
        createAllModels(provider)

        val result = provider.modelStore.queryModels(s"SELECT FROM $peopleCollectionId", None).get
        result.data.toSet shouldBe Set(
          ModelQueryResult(person1MetaData, DataValueToJValue.toJObject(person1Data)),
          ModelQueryResult(person2MetaData, DataValueToJValue.toJObject(person2Data)),
          ModelQueryResult(person3MetaData, DataValueToJValue.toJObject(person3Data)))
      }

      "return correct models if a limit is provided" in withPersistenceStore { provider =>
        createAllModels(provider)

        val result = provider.modelStore.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC LIMIT 2", None).get
        result.data.toSet shouldBe Set(
          ModelQueryResult(person1MetaData, DataValueToJValue.toJObject(person1Data)),
          ModelQueryResult(person2MetaData, DataValueToJValue.toJObject(person2Data)))
      }

      "return correct models if an offset is provided" in withPersistenceStore { provider =>
        createAllModels(provider)

        val result = provider.modelStore.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC OFFSET 1", None).get
        result.data.toSet shouldBe Set(
          ModelQueryResult(person2MetaData, DataValueToJValue.toJObject(person2Data)),
          ModelQueryResult(person3MetaData, DataValueToJValue.toJObject(person3Data)))
      }

      "return correct models if an offset and limit is provided" in withPersistenceStore { provider =>
        createAllModels(provider)

        val result = provider.modelStore.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC LIMIT 1 OFFSET 1", None).get
        result.data shouldBe List(ModelQueryResult(person2MetaData, DataValueToJValue.toJObject(person2Data)))
      }

      "return models in correct order if orderBy ASC is provided" in withPersistenceStore { provider =>
        createAllModels(provider)

        val result = provider.modelStore.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name ASC", None).get
        result.data shouldBe List(
          ModelQueryResult(person1MetaData, DataValueToJValue.toJObject(person1Data)),
          ModelQueryResult(person2MetaData, DataValueToJValue.toJObject(person2Data)),
          ModelQueryResult(person3MetaData, DataValueToJValue.toJObject(person3Data)))
      }

      "return models in correct order if orderBy DESC is provided" in withPersistenceStore { provider =>
        createAllModels(provider)

        val result = provider.modelStore.queryModels(s"SELECT FROM $peopleCollectionId ORDER BY name DESC", None).get
        result.data shouldBe List(
          ModelQueryResult(person3MetaData, DataValueToJValue.toJObject(person3Data)),
          ModelQueryResult(person2MetaData, DataValueToJValue.toJObject(person2Data)),
          ModelQueryResult(person1MetaData, DataValueToJValue.toJObject(person1Data)))
      }
    }

    "deleting a specific model" must {
      "delete the specified model and no others" in withPersistenceStore { provider =>
        createAllModels(provider)

        provider.modelStore.getModel(person1Id).get shouldBe defined
        provider.modelStore.getModel(person2Id).get shouldBe defined
        provider.modelStore.getModel(company1Id).get shouldBe defined

        provider.modelStore.deleteModel(person1Id).get

        provider.modelStore.getModel(person1Id).get shouldBe None
        provider.modelStore.getModel(person2Id).get shouldBe defined
        provider.modelStore.getModel(company1Id).get shouldBe defined
      }

      "return a failure for deleting a non-existent model" in withPersistenceStore { provider =>
        createAllModels(provider)
        provider.modelStore.getModel(notRealId).get shouldBe None
        provider.modelStore.deleteModel(notRealId).failure.exception shouldBe a[EntityNotFoundException]
      }
    }

    "deleting all models in collection" must {
      "delete the models in the specified and no others" in withPersistenceStore { provider =>
        createAllModels(provider)

        provider.modelStore.getModel(person1Id).get shouldBe defined
        provider.modelStore.getModel(person2Id).get shouldBe defined
        provider.modelStore.getModel(person3Id).get shouldBe defined
        provider.modelStore.getModel(company1Id).get shouldBe defined

        provider.modelStore.deleteAllModelsInCollection(peopleCollectionId).success

        provider.modelStore.getModel(person1Id).get shouldBe None
        provider.modelStore.getModel(person2Id).get shouldBe None
        provider.modelStore.getModel(person3Id).get shouldBe None
        provider.modelStore.getModel(company1Id).get shouldBe defined
      }
    }
  }

  def createAllModels(provider: DomainPersistenceProvider): Unit = {
    provider.collectionStore.ensureCollectionExists(companyCollectionId)
    provider.modelStore.createModel(company1Model).get
    createAllPersonModels(provider)
  }

  def createAllPersonModels(provider: DomainPersistenceProvider): Unit = {
    provider.collectionStore.ensureCollectionExists(peopleCollectionId)
    provider.modelStore.createModel(person1Model).get
    provider.modelStore.createModel(person2Model).get
    provider.modelStore.createModel(person3Model).get
  }
}
