package com.convergencelabs.server.datastore.domain

import java.time.Duration

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.model.Collection

// scalastyle:off magic.number
class CollectionStoreSpec
    extends PersistenceStoreSpec[CollectionStore](DeltaCategory.Domain)
    with WordSpecLike
    with Matchers {

  def createStore(dbProvider: DatabaseProvider): CollectionStore = new CollectionStore(
    dbProvider,
    new ModelStore(
      dbProvider,
      new ModelOperationStore(dbProvider),
      new ModelSnapshotStore(dbProvider)))

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

  val peopleCollection = Collection(peopleCollectionId, "People", true, Some(snapshotConfig))
  val copmanyCollection = Collection(companyCollectionId, "Some Company", false, None)
  val teamCollection = Collection(teamCollectionId, "Team", false, None)

  "An ColletionStore" when {

    "asked whether a collection exists" must {
      "return false if it doesn't exist" in withPersistenceStore { store =>
        store.collectionExists(carsCollectionId).success.value shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { store =>
        store.createCollection(peopleCollection)
        store.collectionExists(peopleCollection.id).success.value shouldBe true
      }
    }

    "creating a collection" must {
      "create a collection that is not a duplicate id" in withPersistenceStore { store =>
        store.createCollection(copmanyCollection).success
        store.getCollection(copmanyCollection.id).success.value.value shouldBe copmanyCollection
      }
      
      "properly handle a None snapshot config" in withPersistenceStore { store =>
        store.createCollection(copmanyCollection).success
        store.getCollection(copmanyCollection.id).success.value.value shouldBe copmanyCollection
      }

      "not create a collection that is not a duplicate collection id" in withPersistenceStore { store =>
        store.createCollection(copmanyCollection).success
        store.createCollection(copmanyCollection).success.value shouldBe DuplicateValue
      }
    }

    "getting a collection" must {
      "return None if it doesn't exist" in withPersistenceStore { store =>
        store.getCollection(carsCollectionId).success.value shouldBe None
      }

      "return Some if it does exist" in withPersistenceStore { store =>
        store.createCollection(peopleCollection).success
        store.getCollection(peopleCollectionId).success.value.value shouldBe peopleCollection
      }
    }

    "updatig a collection" must {
      "successfully update an existing collection" in withPersistenceStore { store =>
        store.createCollection(peopleCollection).success
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
        store.createCollection(copmanyCollection).success
        store.createCollection(peopleCollection).success
        store.createCollection(teamCollection).success
        
        val list = store.getAllCollections(None, None).success.value
        list shouldBe List(
          copmanyCollection,
          peopleCollection,
          teamCollection)
      }

      "return only the limited number of meta data when limit provided" in withPersistenceStore { store =>
        store.createCollection(copmanyCollection).success
        store.createCollection(peopleCollection).success
        store.createCollection(teamCollection).success
        
        val list = store.getAllCollections(None, Some(1)).success.value
        list shouldBe List(copmanyCollection)
      }

      "return only the limited number of meta data when offset provided" in withPersistenceStore { store =>
        store.createCollection(copmanyCollection).success
        store.createCollection(peopleCollection).success
        store.createCollection(teamCollection).success
        
        val list = store.getAllCollections(Some(1), None).success.value
        list shouldBe List(
          peopleCollection,
          teamCollection)
      }

      "return only the limited number of meta data when limit and offset provided" in withPersistenceStore { store =>
        store.createCollection(copmanyCollection).success
        store.createCollection(peopleCollection).success
        store.createCollection(teamCollection).success
        
        val list = store.getAllCollections(Some(1), Some(1)).success.value
        list shouldBe List(peopleCollection)
      }
    }

    "deleting a specific collection" must {
      "delete the specified collection and no others" in withPersistenceStore { store =>
        store.createCollection(copmanyCollection).success
        store.createCollection(peopleCollection).success
        store.createCollection(teamCollection).success
        
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
        store.createCollection(peopleCollection).success
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
        store.createCollection(peopleCollection).success
        store.collectionExists(peopleCollectionId).success.value shouldBe true
        store.getOrCreateCollection(peopleCollectionId).success.value shouldBe peopleCollection
      }
    }
  }
}
