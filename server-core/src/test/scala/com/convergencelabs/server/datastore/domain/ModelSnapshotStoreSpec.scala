package com.convergencelabs.server.datastore.domain

import java.text.SimpleDateFormat
import java.time.Instant

import org.json4s.JsonAST.JNull
import org.json4s.JsonAST.JObject
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelSnapshot
import com.convergencelabs.server.domain.model.ModelSnapshotMetaData
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

// scalastyle:off magic.number
class ModelSnapshotStoreSpec
    extends PersistenceStoreSpec[ModelSnapshotStore]("/dbfiles/domain.json.gz")
    with WordSpecLike
    with Matchers {

  override def createStore(dbPool: OPartitionedDatabasePool): ModelSnapshotStore =
    new ModelSnapshotStore(dbPool)

  val CollectionId = "people"
  val person1ModelFqn = ModelFqn(CollectionId, "person1")
  val person2ModelFqn = ModelFqn(CollectionId, "person2")
  val nonExistingModelFqn = ModelFqn(CollectionId, "noPerson")

  val df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  val p1Snapshot0Version = 0L
  val p1Snapshot0Date = df.parse("2015-10-20 01:00:00").getTime

  val inbetweenVersion = 5L

  val p1Snapshot10Version = 10L
  val p1Snapshot10Date = df.parse("2015-10-20 10:00:00").getTime

  val p1Snapshot20Version = 20L
  val p1Snapshot20Date = df.parse("2015-10-20 12:00:00").getTime

  "A ModelSnapshotStore" when {
    "when creating a snapshot" must {
      "be able to get the snapshot that was created" in withPersistenceStore { store =>
        val version = 5L
        val timestamp = Instant.now()

        val created = ModelSnapshot(
          ModelSnapshotMetaData(person1ModelFqn, version, timestamp),
          JObject("key" -> JNull))

        store.createSnapshot(created).success

        val queried = store.getSnapshot(person1ModelFqn, version)
        queried.success.value.value shouldBe created
      }
    }

    "when getting a specific snapshot" must {
      "return the correct snapshot when one exists" in withPersistenceStore { store =>
        val queried = store.getSnapshot(person1ModelFqn, 0L).success.value
        queried shouldBe defined
        // FIXME not specific enough
      }

      "return None when the specified version does not exist" in withPersistenceStore { store =>
        val queried = store.getSnapshot(person1ModelFqn, 5L).success.value
        queried shouldBe None
      }

      "return None when the specified model does not exist" in withPersistenceStore { store =>
        val queried = store.getSnapshot(nonExistingModelFqn, 0L).success.value
        queried shouldBe None
      }
    }

    "when getting all snapshot meta data" must {
      "return all meta data in proper order when no limit or offest is provided" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(person1ModelFqn, None, None).success.value
        metaData.length shouldBe 3
        metaData(0).version shouldBe p1Snapshot0Version
        metaData(1).version shouldBe p1Snapshot10Version
        metaData(2).version shouldBe p1Snapshot20Version
      }

      "correctly limit the number of results with no offset" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(person1ModelFqn, Some(2), None).success.value
        metaData.length shouldBe 2
        metaData(0).version shouldBe p1Snapshot0Version
        metaData(1).version shouldBe p1Snapshot10Version
      }

      "correctly limit the number of results with an offset" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(person1ModelFqn, Some(2), Some(1)).success.value
        metaData.length shouldBe 2
        metaData(0).version shouldBe p1Snapshot10Version
        metaData(1).version shouldBe p1Snapshot20Version
      }

      "correctly offset the results with no limit" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(person1ModelFqn, None, Some(1)).success.value
        metaData.length shouldBe 2
        metaData(0).version shouldBe p1Snapshot10Version
        metaData(1).version shouldBe p1Snapshot20Version
      }

      "return an empty list for a non-existent model" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(nonExistingModelFqn, None, None).success.value
        metaData shouldBe List()
      }
    }

    "when getting snapshots by time" must {
      "return all snapshots if no time or limit-offset" in withPersistenceStore { store =>
        val metaDataList = store.getSnapshotMetaDataForModelByTime(person1ModelFqn, None, None, None, None).success.value
        metaDataList.length shouldBe 3
        metaDataList(0).version shouldBe p1Snapshot0Version
        metaDataList(1).version shouldBe p1Snapshot10Version
        metaDataList(2).version shouldBe p1Snapshot20Version
      }

      "return all snapshots with all encopmasing time bounds and no limit-offset" in withPersistenceStore { store =>
        val metaDataList = store.getSnapshotMetaDataForModelByTime(
          person1ModelFqn, Some(p1Snapshot0Date), Some(p1Snapshot20Date), None, None).success.value
        metaDataList.length shouldBe 3
        metaDataList(0).version shouldBe p1Snapshot0Version
        metaDataList(1).version shouldBe p1Snapshot10Version
        metaDataList(2).version shouldBe p1Snapshot20Version
      }
    }

    "when getting the latest snapshot for a model" must {
      "return the correct meta data for a model with snapshots" in withPersistenceStore { store =>
        val metaData = store.getLatestSnapshotMetaDataForModel(person1ModelFqn).success.value
        metaData.value.version shouldBe p1Snapshot20Version
      }

      "return None when the specified model does not exist" in withPersistenceStore { store =>
        val queried = store.getSnapshot(nonExistingModelFqn, 0L).success.value
        queried shouldBe None
      }
    }

    "when getting the closest snapshot to a version for a model" must {
      "return the higher version when it is the closest" in withPersistenceStore { store =>
        val snapshotData = store.getClosestSnapshotByVersion(person1ModelFqn, 18).success.value
        snapshotData.value.metaData.version shouldBe p1Snapshot20Version
      }

      "return the lower version when it is the closest" in withPersistenceStore { store =>
        val snapshotData = store.getClosestSnapshotByVersion(person1ModelFqn, 14).success.value
        snapshotData.value.metaData.version shouldBe p1Snapshot10Version
      }

      "return the higher version when the requested version is equidistant from a higerh and lower snapshot" in withPersistenceStore { store =>
        val snapshotData = store.getClosestSnapshotByVersion(person1ModelFqn, 15).success.value
        snapshotData.value.metaData.version shouldBe p1Snapshot20Version
      }
    }

    "when removing a single snapshot by model and version" must {
      "remove the specified snapshot and no others" in withPersistenceStore { store =>
        store.getSnapshot(person1ModelFqn, 0L).success.value shouldBe defined
        store.removeSnapshot(person1ModelFqn, 0L)
        store.getSnapshot(person1ModelFqn, 0L).success.value shouldBe None

        // Ensure no others were deleted from the desired model
        val person1MetaData = store.getSnapshotMetaDataForModel(person1ModelFqn, None, None).success.value
        person1MetaData.length shouldBe 2

        person1MetaData(0).version shouldBe p1Snapshot10Version
        person1MetaData(1).version shouldBe p1Snapshot20Version

        // Ensure no others were deleted from the other model
        val person2MetaData = store.getSnapshotMetaDataForModel(person2ModelFqn, None, None).success.value
        person2MetaData.length shouldBe 1
      }
    }

    "when removing all snapshots by model and version" must {
      "remove the snapshots for the specified model and no others" in withPersistenceStore { store =>
        store.getSnapshotMetaDataForModel(person1ModelFqn, None, None).success.value.length shouldBe 3
        store.removeAllSnapshotsForModel(person1ModelFqn)
        store.getSnapshotMetaDataForModel(person1ModelFqn, None, None).success.value.length shouldBe 0

        // Ensure no others were deleted from the other model
        store.getSnapshotMetaDataForModel(person2ModelFqn, None, None).success.value.length shouldBe 1
      }
    }

    "when removing all snapshots" must {
      "remove all snapshots for all models in a collection" in withPersistenceStore { store =>
        store.getSnapshotMetaDataForModel(person1ModelFqn, None, None).success.value.length shouldBe 3
        store.getSnapshotMetaDataForModel(person2ModelFqn, None, None).success.value.length shouldBe 1

        store.removeAllSnapshotsForCollection(person1ModelFqn.collectionId)

        store.getSnapshotMetaDataForModel(person1ModelFqn, None, None).success.value.length shouldBe 0
        store.getSnapshotMetaDataForModel(person2ModelFqn, None, None).success.value.length shouldBe 0

      }
    }
  }
}
