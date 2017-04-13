package com.convergencelabs.server.datastore.domain

import java.time.Instant

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.ModelSnapshot
import com.convergencelabs.server.domain.model.ModelSnapshotMetaData
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue

// scalastyle:off magic.number
class ModelSnapshotStoreSpec
    extends PersistenceStoreSpec[DomainPersistenceProvider](DeltaCategory.Domain)
    with WordSpecLike
    with Matchers {

  override def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider =
    new DomainPersistenceProvider(dbProvider)

  val modelPermissions = ModelPermissions(true, true, true, true)
  
  val CollectionId = "people"

  val person1Id = "person1"
  val person1ModelFqn = ModelFqn(CollectionId, person1Id)
  val person1ModelData = ObjectValue("vid", Map("value" -> DoubleValue("0:1", 1)))
  val person1ModelMetaData = ModelMetaData(CollectionId, person1Id, 10L, Instant.now(), Instant.now(), true, modelPermissions)
  val person1Model = Model(person1ModelMetaData, person1ModelData)

  val p1Snapshot1Version = 1L
  val p1Snapshot1Date = Instant.parse("2016-01-01T00:00:00Z")
  val p1Snapshot1MetaData = ModelSnapshotMetaData(person1ModelFqn, p1Snapshot1Version, p1Snapshot1Date)
  val p1Snapshot1Data = ObjectValue("vid", Map("value" -> DoubleValue("0:1", 1)))
  val p1Snapshot1 = ModelSnapshot(p1Snapshot1MetaData, p1Snapshot1Data)

  val inbetweenVersion = 5L

  val p1Snapshot10Version = 10L
  val p1Snapshot10Date = Instant.parse("2016-01-10T00:00:00Z")
  val p1Snapshot10MetaData = ModelSnapshotMetaData(person1ModelFqn, p1Snapshot10Version, p1Snapshot10Date)
  val p1Snapshot10Data = ObjectValue("vid", Map("value" -> DoubleValue("0:1", 10)))
  val p1Snapshot10 = ModelSnapshot(p1Snapshot10MetaData, p1Snapshot10Data)

  val p1Snapshot20Version = 20L
  val p1Snapshot20Date = Instant.parse("2016-01-20T00:00:00Z")
  val p1Snapshot20MetaData = ModelSnapshotMetaData(person1ModelFqn, p1Snapshot20Version, p1Snapshot20Date)
  val p1Snapshot20Data = ObjectValue("vid", Map("value" -> DoubleValue("0:1", 20)))
  val p1Snapshot20 = ModelSnapshot(p1Snapshot20MetaData, p1Snapshot20Data)
  
  val person2Id = "person2"
  val person2ModelFqn = ModelFqn(CollectionId, person2Id)
  val person2ModelData = ObjectValue("vid", Map("value" -> DoubleValue("0:1", 1)))
  val person2ModelMetaData = ModelMetaData(CollectionId, person2Id, 10L, Instant.now(), Instant.now(), true, modelPermissions)
  val person2Model = Model(person2ModelMetaData, person2ModelData)
  
  val p2Snapshot1Version = 20L
  val p2Snapshot1Date = Instant.parse("2016-01-20T00:00:00Z")
  val p2Snapshot1MetaData = ModelSnapshotMetaData(person2ModelFqn, 1L, Instant.now())
  val p2Snapshot1Data = ObjectValue("vid", Map("value" -> DoubleValue("0:1", 1)))
  val p2Snapshot1 = ModelSnapshot(p2Snapshot1MetaData, p2Snapshot1Data)
  
  val noPersonId = "noPerson"
  val nonExistingModelFqn = ModelFqn(CollectionId, noPersonId)

  "A ModelSnapshotStore" when {
    "creating a snapshot" must {
      "be able to get the snapshot that was created" in withPersistenceStore { provider =>
        initModels(provider)
        
        val version = 5L
        val timestamp = Instant.now()

        val created = ModelSnapshot(
          ModelSnapshotMetaData(person1ModelFqn, version, timestamp),
          ObjectValue("0:0", Map("key" -> StringValue("0:1", "value"))))

        provider.modelSnapshotStore.createSnapshot(created).success

        val queried = provider.modelSnapshotStore.getSnapshot(person1Id, version)
        queried.success.value.value shouldBe created
      }
    }

    "when getting a specific snapshot" must {
      "return the correct snapshot when one exists" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val queried = provider.modelSnapshotStore.getSnapshot(person1Id, 1L).success.value
        queried.value shouldBe p1Snapshot1
      }

      "return None when the specified version does not exist" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val queried = provider.modelSnapshotStore.getSnapshot(person1Id, 5L).success.value
        queried shouldBe None
      }

      "return None when the specified model does not exist" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val queried = provider.modelSnapshotStore.getSnapshot(noPersonId, 1L).success.value
        queried shouldBe None
      }
    }

    "when getting all snapshot meta data" must {
      "return all meta data in proper order when no limit or offest is provided" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val metaData = provider.modelSnapshotStore.getSnapshotMetaDataForModel(person1Id, None, None).success.value
        metaData.length shouldBe 3
        metaData(0).version shouldBe p1Snapshot1Version
        metaData(1).version shouldBe p1Snapshot10Version
        metaData(2).version shouldBe p1Snapshot20Version
      }

      "correctly limit the number of results with no offset" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val metaData = provider.modelSnapshotStore.getSnapshotMetaDataForModel(person1Id, Some(2), None).success.value
        metaData.length shouldBe 2
        metaData(0).version shouldBe p1Snapshot1Version
        metaData(1).version shouldBe p1Snapshot10Version
      }

      "correctly limit the number of results with an offset" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val metaData = provider.modelSnapshotStore.getSnapshotMetaDataForModel(person1Id, Some(2), Some(1)).success.value
        metaData.length shouldBe 2
        metaData(0).version shouldBe p1Snapshot10Version
        metaData(1).version shouldBe p1Snapshot20Version
      }

      "correctly offset the results with no limit" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val metaData = provider.modelSnapshotStore.getSnapshotMetaDataForModel(person1Id, None, Some(1)).success.value
        metaData.length shouldBe 2
        metaData(0).version shouldBe p1Snapshot10Version
        metaData(1).version shouldBe p1Snapshot20Version
      }

      "return an empty list for a non-existent model" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val metaData = provider.modelSnapshotStore.getSnapshotMetaDataForModel(noPersonId, None, None).success.value
        metaData shouldBe List()
      }
    }

    "when getting snapshots by time" must {
      "return all snapshots if no time or limit-offset" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val metaDataList = provider.modelSnapshotStore.getSnapshotMetaDataForModelByTime(person1Id, None, None, None, None).success.value
        metaDataList.length shouldBe 3
        metaDataList(0).version shouldBe p1Snapshot1Version
        metaDataList(1).version shouldBe p1Snapshot10Version
        metaDataList(2).version shouldBe p1Snapshot20Version
      }

      "return all snapshots with all encopmasing time bounds and no limit-offset" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val metaDataList = provider.modelSnapshotStore.getSnapshotMetaDataForModelByTime(
          person1Id, Some(p1Snapshot1Date.toEpochMilli), Some(p1Snapshot20Date.toEpochMilli), None, None).success.value
        metaDataList.length shouldBe 3
        metaDataList(0).version shouldBe p1Snapshot1Version
        metaDataList(1).version shouldBe p1Snapshot10Version
        metaDataList(2).version shouldBe p1Snapshot20Version
      }
    }

    "when getting the latest snapshot for a model" must {
      "return the correct meta data for a model with snapshots" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val metaData = provider.modelSnapshotStore.getLatestSnapshotMetaDataForModel(person1Id).success.value
        metaData.value.version shouldBe p1Snapshot20Version
      }

      "return None when the specified model does not exist" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val queried = provider.modelSnapshotStore.getSnapshot(noPersonId, 1L).success.value
        queried shouldBe None
      }
    }

    "when getting the closest snapshot to a version for a model" must {
      "return the higher version when it is the closest" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val snapshotData = provider.modelSnapshotStore.getClosestSnapshotByVersion(person1Id, 18).success.value
        snapshotData.value.metaData.version shouldBe p1Snapshot20Version
      }

      "return the lower version when it is the closest" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val snapshotData = provider.modelSnapshotStore.getClosestSnapshotByVersion(person1Id, 14).success.value
        snapshotData.value.metaData.version shouldBe p1Snapshot10Version
      }

      "return the higher version when the requested version is equidistant from a higerh and lower snapshot" in withPersistenceStore { provider =>
        createSnapshots(provider)
        val snapshotData = provider.modelSnapshotStore.getClosestSnapshotByVersion(person1Id, 15).success.value
        snapshotData.value.metaData.version shouldBe p1Snapshot20Version
      }
    }

    "when removing a single snapshot by model and version" must {
      "remove the specified snapshot and no others" in withPersistenceStore { provider =>
        createSnapshots(provider)
        provider.modelSnapshotStore.getSnapshot(person1Id, p1Snapshot1Version).success.value shouldBe defined
        provider.modelSnapshotStore.removeSnapshot(person1Id, p1Snapshot1Version)
        provider.modelSnapshotStore.getSnapshot(person1Id, p1Snapshot1Version).success.value shouldBe None

        // Ensure no others were deleted from the desired model
        val person1MetaData = provider.modelSnapshotStore.getSnapshotMetaDataForModel(person1Id, None, None).success.value
        person1MetaData.length shouldBe 2

        person1MetaData(0).version shouldBe p1Snapshot10Version
        person1MetaData(1).version shouldBe p1Snapshot20Version

        // Ensure no others were deleted from the other model
        val person2MetaData = provider.modelSnapshotStore.getSnapshotMetaDataForModel(person2Id, None, None).success.value
        person2MetaData.length shouldBe 1
      }
    }

    "when removing all snapshots by model and version" must {
      "remove the snapshots for the specified model and no others" in withPersistenceStore { provider =>
        createSnapshots(provider)
        provider.modelSnapshotStore.getSnapshotMetaDataForModel(person1Id, None, None).success.value.length shouldBe 3
        provider.modelSnapshotStore.removeAllSnapshotsForModel(person1Id)
        provider.modelSnapshotStore.getSnapshotMetaDataForModel(person1Id, None, None).success.value.length shouldBe 0

        // Ensure no others were deleted from the other model
        provider.modelSnapshotStore.getSnapshotMetaDataForModel(person2Id, None, None).success.value.length shouldBe 1
      }
    }

    "when removing all snapshots" must {
      "remove all snapshots for all models in a collection" in withPersistenceStore { provider =>
        createSnapshots(provider)
        
        provider.modelSnapshotStore.getSnapshotMetaDataForModel(person1Id, None, None).success.value.length shouldBe 3
        provider.modelSnapshotStore.getSnapshotMetaDataForModel(person2Id, None, None).success.value.length shouldBe 1

        provider.modelSnapshotStore.removeAllSnapshotsForCollection(person1ModelFqn.collectionId).success

        provider.modelSnapshotStore.getSnapshotMetaDataForModel(person1Id, None, None).success.value.length shouldBe 0
        provider.modelSnapshotStore.getSnapshotMetaDataForModel(person2Id, None, None).success.value.length shouldBe 0
      }
    }
  }

  def initModels(provider: DomainPersistenceProvider): Unit = {
    provider.collectionStore.ensureCollectionExists(person1ModelFqn.collectionId).get
    provider.modelStore.createModel(person1Model).get
    provider.modelStore.createModel(person2Model).get
  }

  def createSnapshots(provider: DomainPersistenceProvider): Unit = {
    initModels(provider)
    provider.modelSnapshotStore.createSnapshot(p1Snapshot1).success
    provider.modelSnapshotStore.createSnapshot(p1Snapshot10).success
    provider.modelSnapshotStore.createSnapshot(p1Snapshot20).success
    provider.modelSnapshotStore.createSnapshot(p2Snapshot1).success
  }
}
