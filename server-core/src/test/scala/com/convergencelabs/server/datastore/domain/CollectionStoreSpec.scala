package com.convergencelabs.server.datastore.domain

import java.text.SimpleDateFormat
import java.time.Instant
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelMetaData
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import java.util.concurrent.TimeUnit
import java.time.Duration
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.model.Collection
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.NotFound

// scalastyle:off magic.number
class CollectionStoreSpec
    extends PersistenceStoreSpec[CollectionStore]("/dbfiles/domain-n1-d1.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): CollectionStore = new CollectionStore(
    dbPool,
    new ModelStore(
      dbPool,
      new ModelOperationStore(dbPool),
      new ModelSnapshotStore(dbPool)))

  val companyCollectionId = "company"
  val peopleCollectionId = "people"
  val teamCollectionId = "team"
  val carsCollectionId = "cars"

  val snapshotConfig = ModelSnapshotConfig(
    true,
    true,
    true,
    250,
    500,
    false,
    false,
    Duration.ofSeconds(0),
    Duration.ofSeconds(0))

  val expectedPeopleCollection = Collection(peopleCollectionId, "People", true, Some(snapshotConfig))
  val expectedCopmanyCollection = Collection(companyCollectionId, "Company", false, None)
  val expectedTeamCollection = Collection(teamCollectionId, "Team", false, None)

  "An ColletionStore" when {

    "asked whether a collection exists" must {

      "return false if it doesn't exist" in withPersistenceStore { store =>
        store.collectionExists(carsCollectionId).success.value shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { store =>
        store.collectionExists(peopleCollectionId).success.value shouldBe true
      }
    }

    "creating a collection" must {
      "create a collection that is not a duplicate model fqn" in withPersistenceStore { store =>
        val collection = Collection(carsCollectionId, "a name", true, Some(snapshotConfig))

        store.createCollection(collection).success
        store.getCollection(carsCollectionId).success.value.value shouldBe collection
      }

      "not create a collection that is not a duplicate collection id" in withPersistenceStore { store =>
        val collection = Collection(peopleCollectionId, "a name", true, Some(snapshotConfig))
        store.createCollection(collection).success.value shouldBe DuplicateValue
      }
    }

    "getting a collection" must {
      "return None if it doesn't exist" in withPersistenceStore { store =>
        store.getCollection(carsCollectionId).success.value shouldBe None
      }

      "return Some if it does exist" in withPersistenceStore { store =>
        store.getCollection(peopleCollectionId).success.value shouldBe defined
      }
    }

    "updatig a collection" must {
      "successfully update an existing collection" in withPersistenceStore { store =>
        val existing = store.getCollection(peopleCollectionId).success.value.value
        val updated = existing.copy(name = "foo", overrideSnapshotConfig = false, snapshotConfig = None)
        store.updateCollection(updated).success.value
        store.getCollection(peopleCollectionId).success.value.value shouldBe updated
      }

      "return NotFound on a collection that does not exist" in withPersistenceStore { store =>
        val toUpdate = Collection(carsCollectionId, "", false, None)
        store.updateCollection(toUpdate).success.value shouldBe NotFound
      }
    }

    "getting all collections" must {
      "return all meta data when no limit or offset are provided" in withPersistenceStore { store =>
        val list = store.getAllCollections(None, None).success.value
        list shouldBe List(
          expectedCopmanyCollection,
          expectedPeopleCollection,
          expectedTeamCollection)
      }

      "return only the limited number of meta data when limit provided" in withPersistenceStore { store =>
        val list = store.getAllCollections(None, Some(1)).success.value
        list shouldBe List(expectedCopmanyCollection)
      }

      "return only the limited number of meta data when offset provided" in withPersistenceStore { store =>
        val list = store.getAllCollections(Some(1), None).success.value
        list shouldBe List(
          expectedPeopleCollection,
          expectedTeamCollection)
      }

      "return only the limited number of meta data when limit and offset provided" in withPersistenceStore { store =>
        val list = store.getAllCollections(Some(1), Some(1)).success.value
        list shouldBe List(expectedPeopleCollection)
      }
    }

    "deleting a specific collection" must {
      "delete the specified collection and no others" in withPersistenceStore { store =>
        store.getCollection(peopleCollectionId).success.value shouldBe defined
        store.getCollection(companyCollectionId).success.value shouldBe defined
        store.getCollection(teamCollectionId).success.value shouldBe defined

        store.deleteCollection(peopleCollectionId).success

        store.getCollection(peopleCollectionId).success.value shouldBe None
        store.getCollection(companyCollectionId).success.value shouldBe defined
        store.getCollection(teamCollectionId).success.value shouldBe defined
      }

      "return NotFound for deleting a non-existent collection" in withPersistenceStore { store =>
        store.getCollection(carsCollectionId).success.value shouldBe None
        store.deleteCollection(carsCollectionId).success.value shouldBe NotFound
      }
    }

    "ensuring a collection exists" must {
      "creates a collection that doesn't exist" in withPersistenceStore { store =>
        store.collectionExists(carsCollectionId).success.value shouldBe false
        store.ensureCollectionExists(carsCollectionId).success
        store.collectionExists(carsCollectionId).success.value shouldBe true
      }

      "does not error for a collection that exists" in withPersistenceStore { store =>
        store.collectionExists(peopleCollectionId).success.value shouldBe true
        store.ensureCollectionExists(peopleCollectionId).success
      }
    }

    "getting or creating collection" must {
      "creates a collection that doesn't exist" in withPersistenceStore { store =>
        store.collectionExists(carsCollectionId).success.value shouldBe false
        store.getOrCreateCollection(carsCollectionId).success
      }

      "gets a collection that exists" in withPersistenceStore { store =>
        store.collectionExists(peopleCollectionId).success.value shouldBe true
        store.getOrCreateCollection(peopleCollectionId).success.value shouldBe expectedPeopleCollection
      }
    }
  }
}
