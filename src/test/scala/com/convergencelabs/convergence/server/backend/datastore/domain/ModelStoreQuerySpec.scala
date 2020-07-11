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

import java.time.Instant

import com.convergencelabs.convergence.server.backend.datastore.domain.collection.CollectionStore
import com.convergencelabs.convergence.server.backend.datastore.domain.model.{ModelOperationStore, ModelPermissionsStore, ModelSnapshotStore, ModelStore}
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserStore
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.schema.legacy.DeltaCategory
import com.convergencelabs.convergence.server.model.domain.model
import com.convergencelabs.convergence.server.model.domain.model._
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId, DomainUserType}
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{DefaultFormats, Formats, JArray, JBool, JField, JInt, JObject, JString, jvalue2extractable, jvalue2monadic, string2JsonInput}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.language.postfixOps

case class ModelStoreQuerySpecStores(collection: CollectionStore, model: ModelStore, user: DomainUserStore, permissions: ModelPermissionsStore)

// scalastyle:off magic.number
class ModelStoreQuerySpec extends PersistenceStoreSpec[ModelStoreQuerySpecStores](DeltaCategory.Domain) with AnyWordSpecLike with Matchers {

  private implicit val formats: Formats = DefaultFormats
  private var vid = 0

  def createStore(dbProvider: DatabaseProvider): ModelStoreQuerySpecStores = {
    val modelStore = new ModelStore(dbProvider, new ModelOperationStore(dbProvider), new ModelSnapshotStore(dbProvider))
    val collectionStore = new CollectionStore(dbProvider)
    val userStore = new DomainUserStore(dbProvider)
    val modelPermissionsStore = new ModelPermissionsStore(dbProvider)
    ModelStoreQuerySpecStores(collectionStore, modelStore, userStore, modelPermissionsStore)
  }

  "Querying a ModelStore" when {

    "using order by" must {
      "return correct order when using ASC on top level field" in withPersistenceStore { stores =>
        createModels(stores)
        val results = stores.model.queryModels("SELECT FROM collection1 ORDER BY sField ASC", None).get
        results.data.map {
          _.metaData.id
        } shouldEqual List("model1", "model2")
      }
      "return correct order when using DESC on top level field" in withPersistenceStore { stores =>
        createModels(stores)
        val results = stores.model.queryModels("SELECT FROM collection1 ORDER BY sField DESC", None).get
        results.data.map {
          _.metaData.id
        } shouldEqual List("model2", "model1")
      }
      "return correct order when using ASC on field inside top level array" in withPersistenceStore { stores =>
        createModels(stores)
        val list = stores.model.queryModels("SELECT FROM collection1 ORDER BY arrayField[0] ASC", None).get
        list.data.map {
          _.metaData.id
        } shouldEqual List("model1", "model2")
      }
      "return correct order when using DESC on field inside top level array" in withPersistenceStore { stores =>
        createModels(stores)
        val results = stores.model.queryModels("SELECT FROM collection1 ORDER BY arrayField[0] DESC", None).get
        results.data.map {
          _.metaData.id
        } shouldEqual List("model2", "model1")
      }
      "return correct order when using ASC on field inside second level object" in withPersistenceStore { stores =>
        createModels(stores)
        val results = stores.model.queryModels("SELECT FROM collection1 ORDER BY oField.oField2 ASC", None).get
        results.data.map {
          _.metaData.id
        } shouldEqual List("model1", "model2")
      }
      "return correct order when using DESC on field inside second level object" in withPersistenceStore { stores =>
        createModels(stores)
        val results = stores.model.queryModels("SELECT FROM collection1 ORDER BY oField.oField2 DESC", None).get
        results.data.map {
          _.metaData.id
        } shouldEqual List("model2", "model1")
      }
      "return correct order when using ASC on field only one model has" in withPersistenceStore { stores =>
        createModels(stores)
        val results = stores.model.queryModels("SELECT FROM collection1 ORDER BY model1Field ASC", None).get
        results.data.map {
          _.metaData.id
        } shouldEqual List("model2", "model1")
      }
      "return correct order when using DESC on field only one model has" in withPersistenceStore { stores =>
        createModels(stores)
        val results = stores.model.queryModels("SELECT FROM collection1 ORDER BY model1Field DESC", None).get
        results.data.map {
          _.metaData.id
        } shouldEqual List("model1", "model2")
      }
    }

    "permissions are set" must {
      "return correct models when world is set to false" in withPersistenceStore { stores =>
        createModels(stores)
        createUsers(stores)

        stores.permissions.setOverrideCollectionPermissions("model1", overridePermissions = true)
        stores.permissions.setOverrideCollectionPermissions("model2", overridePermissions = true)
        stores.permissions.setModelWorldPermissions("model1", ModelPermissions(read = false, write = false, remove = false, manage = false)).get

        val results = stores.model.queryModels("SELECT FROM collection1 ORDER BY sField ASC", Some(DomainUserId.normal("test1"))).get
        results.data.map {
          _.metaData.id
        } shouldEqual (List("model2"))
      }

      "return correct models when world is set to false but user permission is true" in withPersistenceStore { stores =>
        createModels(stores)
        createUsers(stores)

        stores.permissions.setOverrideCollectionPermissions("model1", overridePermissions = true)
        stores.permissions.setOverrideCollectionPermissions("model2", overridePermissions = true)
        stores.permissions.setModelWorldPermissions("model1", ModelPermissions(read = false, write = false, remove = false, manage = false))
        stores.permissions.updateModelUserPermissions("model1", DomainUserId.normal("test1"), ModelPermissions(read = true, write = true, remove = true, manage = true))

        val results = stores.model.queryModels("SELECT FROM collection1 ORDER BY sField ASC", Some(DomainUserId.normal("test1"))).get
        results.data.map {
          _.metaData.id
        } shouldEqual (List("model1", "model2"))
      }
    }

    "projection is used" must {
      "return correct fields when projection is used" in withPersistenceStore { stores =>
        createModels(stores)
        val results = stores.model.queryModels("SELECT sField FROM collection1 WHERE bField = false ORDER BY sField ASC", None).get
        results.data.map {
          _.metaData.id
        } shouldEqual List("model2")
        results.data.map {
          _.data
        } shouldEqual List(JObject(List(("sField", JString("myString2")))))
      }
    }
  }

  private[this] def jsonStringToModel(jsonString: String): Model = {
    val json = parse(jsonString)

    val collectionId = (json \ "collection").extract[String]
    val modelId = (json \ "id").extract[String]
    val version = (json \ "version").extract[Long]
    val created = (json \ "created").extract[Long]
    val modified = (json \ "modified").extract[Long]

    val modelPermissions = ModelPermissions(read = true, write = true, remove = true, manage = true)

    val metaData = ModelMetaData(modelId, collectionId, version, Instant.ofEpochMilli(created), Instant.ofEpochMilli(modified), overridePermissions = true, modelPermissions, 1)

    model.Model(metaData, jObjectToObjectValue((json \ "data").asInstanceOf[JObject]))
  }

  private[this] def jObjectToObjectValue(jObject: JObject): ObjectValue = {
    val fieldMap = jObject.obj.map {
      case JField(field, value) =>
        field -> (value match {
          case value: JObject => jObjectToObjectValue(value)
          case value: JArray => jArrayToArrayValue(value)
          case JString(value) => StringValue(nextId(), value)
          case JBool(value) => BooleanValue(nextId(), value)
          case JInt(value) => DoubleValue(nextId(), value.toDouble)
          case _ => ???
        })
    }
    model.ObjectValue(nextId(), fieldMap toMap)
  }

  private[this] def jArrayToArrayValue(jArray: JArray): ArrayValue = {
    val fields = jArray.arr.map {
      case value: JObject => jObjectToObjectValue(value)
      case value: JArray => jArrayToArrayValue(value)
      case JString(value) => StringValue(nextId(), value)
      case JBool(value) => BooleanValue(nextId(), value)
      case JInt(value) => DoubleValue(nextId(), value.toDouble)
      case _ => ???
    }
    model.ArrayValue(nextId(), fields)
  }

  private[this] def nextId(): String = {
    vid += 1
    s"$vid"
  }

  private[this] def createModels(stores: ModelStoreQuerySpecStores): Unit = {
    stores.collection.ensureCollectionExists("collection1")
    stores.model.createModel(jsonStringToModel(
      """{
           "collection": "collection1",
           "id": "model1",
           "version": 10,
           "created": 1486577481766,
           "modified": 1486577481766,
           "data": {
             "sField": "myString",
             "bField": true,
             "iField": 25,
             "oField": {
               "oField1": "model1String",
               "oField2": 1
             },
             "arrayField": ["A", "B", "C"],
             "model1Field": "onlyIHaveThis"
           }
          }""")).get

    stores.model.createModel(jsonStringToModel(
      """{
           "collection": "collection1",
           "id": "model2",
           "version": 20,
           "created": 1486577481766,
           "modified": 1486577481766,
           "data": {
             "sField": "myString2",
             "bField": false,
             "iField": 28,
             "oField": {
               "oField1": "model2String",
               "oField2": 2
             },
             "arrayField": ["D", "E", "F"]
           }
          }""")).get
  }

  private[this] def createUsers(stores: ModelStoreQuerySpecStores): Unit = {
    stores.user.createDomainUser(DomainUser(DomainUserType.Normal, "test1", None, None, None, None, None))
    stores.user.createDomainUser(DomainUser(DomainUserType.Normal, "test2", None, None, None, None, None))
  }
}
