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

import java.time.Duration

import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.datastore.domain.collection.CollectionStore
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.schema.legacy.DeltaCategory
import com.convergencelabs.convergence.server.model.domain
import com.convergencelabs.convergence.server.model.domain.{ModelSnapshotConfig, collection}
import com.convergencelabs.convergence.server.model.domain.collection.{Collection, CollectionPermissions}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

// scalastyle:off magic.number
class CollectionStoreSpec
  extends PersistenceStoreSpec[CollectionStore](DeltaCategory.Domain)
    with AnyWordSpecLike
    with Matchers {

  def createStore(dbProvider: DatabaseProvider): CollectionStore = new CollectionStore(dbProvider)

  private val companyCollectionId = "company"
  private val peopleCollectionId = "people"
  private val teamCollectionId = "team"
  private val carsCollectionId = "cars"

  private val snapshotConfig = domain.ModelSnapshotConfig(
    snapshotsEnabled = true,
    triggerByVersion = true,
    limitedByVersion = true,
    250,
    500,
    triggerByTime = false,
    limitedByTime = false,
    Duration.ofSeconds(0),
    Duration.ofSeconds(0))

  private val collectionPermissions = CollectionPermissions(create = true, read = true, write = true, remove = true, manage = true)

  private val peopleCollection = collection.Collection(peopleCollectionId, "People", overrideSnapshotConfig = true, snapshotConfig, collectionPermissions)
  private val companyCollection = collection.Collection(companyCollectionId, "Some Company", overrideSnapshotConfig = false, snapshotConfig, collectionPermissions)
  private val teamCollection = collection.Collection(teamCollectionId, "Team", overrideSnapshotConfig = false, snapshotConfig, collectionPermissions)

  "An CollectionStore" when {

    "asked whether a collection exists" must {
      "return false if it doesn't exist" in withPersistenceStore { store =>
        store.collectionExists(carsCollectionId).get shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { store =>
        store.createCollection(peopleCollection)
        store.collectionExists(peopleCollection.id).get shouldBe true
      }
    }

    "creating a collection" must {
      "create a collection that is not a duplicate id" in withPersistenceStore { store =>
        store.createCollection(companyCollection).get
        store.getCollection(companyCollection.id).get.value shouldBe companyCollection
      }

      "properly handle a None snapshot config" in withPersistenceStore { store =>
        store.createCollection(companyCollection).get
        store.getCollection(companyCollection.id).get.value shouldBe companyCollection
      }

      "not create a collection that is not a duplicate collection id" in withPersistenceStore { store =>
        store.createCollection(companyCollection).get
        store.createCollection(companyCollection).failure.exception shouldBe a[DuplicateValueException]
      }
    }

    "getting a collection" must {
      "return None if it doesn't exist" in withPersistenceStore { store =>
        store.getCollection(carsCollectionId).get shouldBe None
      }

      "return Some if it does exist" in withPersistenceStore { store =>
        store.createCollection(peopleCollection).get
        store.getCollection(peopleCollectionId).get.value shouldBe peopleCollection
      }
    }

    "updating a collection" must {
      "successfully update an existing collection" in withPersistenceStore { store =>
        store.createCollection(peopleCollection).get
        val existing = store.getCollection(peopleCollectionId).get.value
        val updated = existing.copy(description = "updatedPeople", overrideSnapshotConfig = false, snapshotConfig = snapshotConfig)
        store.updateCollection(existing.id, updated).get
        store.getCollection(peopleCollectionId).get.value shouldBe updated
      }

      "successfully update an existing collection to a new id" in withPersistenceStore { store =>
        store.createCollection(peopleCollection).get
        val existing = store.getCollection(peopleCollectionId).get.value
        val newId = "newId"
        val updated = existing.copy(
          id = newId,
          description = "updatedPeople",
          overrideSnapshotConfig = false,
          snapshotConfig = snapshotConfig)
        store.updateCollection(existing.id, updated).get
        store.getCollection(newId).get.value shouldBe updated
        store.getCollection(peopleCollectionId).get shouldBe None
      }

      "not allow a collection to be updated with an id that is already taken" in withPersistenceStore { store =>
        store.createCollection(peopleCollection).get
        store.createCollection(companyCollection)
        val existing = store.getCollection(peopleCollectionId).get.value
        val updated = existing.copy(id = companyCollection.id)
        store.updateCollection(existing.id, updated).failure.exception shouldBe a[DuplicateValueException]
      }


      "return EntityNotFoundException on a collection that does not exist" in withPersistenceStore { store =>
        val toUpdate = collection.Collection(carsCollectionId, "", overrideSnapshotConfig = false, snapshotConfig, collectionPermissions)
        store.updateCollection(carsCollectionId, toUpdate).failure.exception shouldBe a[EntityNotFoundException]
      }
    }

    "getting all collections" must {
      "return all meta data when no limit or offset are provided" in withPersistenceStore { store =>
        store.createCollection(companyCollection).get
        store.createCollection(peopleCollection).get
        store.createCollection(teamCollection).get

        val list = store.getAllCollections(None, QueryOffset(), QueryLimit()).get
        list shouldBe PagedData(List(
          companyCollection,
          peopleCollection,
          teamCollection), 0, 3)
      }

      "return only the limited number of meta data when limit provided" in withPersistenceStore { store =>
        store.createCollection(companyCollection).get
        store.createCollection(peopleCollection).get
        store.createCollection(teamCollection).get

        val list = store.getAllCollections(None, QueryOffset(), QueryLimit(1)).get
        list shouldBe PagedData(List(companyCollection), 0, 3)
      }

      "return only the limited number of meta data when offset provided" in withPersistenceStore { store =>
        store.createCollection(companyCollection).get
        store.createCollection(peopleCollection).get
        store.createCollection(teamCollection).get

        val list = store.getAllCollections(None, QueryOffset(1), QueryLimit()).get
        list shouldBe PagedData(List(peopleCollection, teamCollection), 1, 3)
      }

      "return only the limited number of meta data when limit and offset provided" in withPersistenceStore { store =>
        store.createCollection(companyCollection).get
        store.createCollection(peopleCollection).get
        store.createCollection(teamCollection).get

        val list = store.getAllCollections(None, QueryOffset(1), QueryLimit(1)).get
        list shouldBe PagedData(List(peopleCollection), 1, 3)
      }
    }

    "deleting a specific collection" must {
      "delete the specified collection and no others" in withPersistenceStore { store =>
        store.createCollection(companyCollection).get
        store.createCollection(peopleCollection).get
        store.createCollection(teamCollection).get

        store.getCollection(peopleCollectionId).get shouldBe defined
        store.getCollection(companyCollectionId).get shouldBe defined
        store.getCollection(teamCollectionId).get shouldBe defined

        store.deleteCollection(peopleCollectionId).get

        store.getCollection(peopleCollectionId).get shouldBe None
        store.getCollection(companyCollectionId).get shouldBe defined
        store.getCollection(teamCollectionId).get shouldBe defined
      }

      "return EntityNotFoundException for deleting a non-existent collection" in withPersistenceStore { store =>
        store.getCollection(carsCollectionId).get shouldBe None
        store.deleteCollection(carsCollectionId).failure.exception shouldBe a[EntityNotFoundException]
      }
    }

    "ensuring a collection exists" must {
      "creates a collection that doesn't exist" in withPersistenceStore { store =>
        store.collectionExists(carsCollectionId).get shouldBe false
        store.ensureCollectionExists(carsCollectionId).get
        store.collectionExists(carsCollectionId).get shouldBe true
      }

      "does not error for a collection that exists" in withPersistenceStore { store =>
        store.createCollection(peopleCollection).get
        store.collectionExists(peopleCollectionId).get shouldBe true
        store.ensureCollectionExists(peopleCollectionId).get
      }
    }

    "getting or creating collection" must {
      "creates a collection that doesn't exist" in withPersistenceStore { store =>
        store.collectionExists(carsCollectionId).get shouldBe false
        store.getOrCreateCollection(carsCollectionId).get
      }

      "gets a collection that exists" in withPersistenceStore { store =>
        store.createCollection(peopleCollection).get
        store.collectionExists(peopleCollectionId).get shouldBe true
        store.getOrCreateCollection(peopleCollectionId).get shouldBe peopleCollection
      }
    }
  }
}
