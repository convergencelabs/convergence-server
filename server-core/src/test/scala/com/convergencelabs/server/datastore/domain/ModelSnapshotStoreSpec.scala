package com.convergencelabs.server.datastore.domain

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.BeforeAndAfterAll
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.common.log.OLogManager
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JNull
import org.json4s.JsonAST.JInt
import java.text.SimpleDateFormat
import java.time.Instant
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.OptionValues._
import org.scalatest.TryValues._

class ModelSnapshotStoreSpec
    extends PersistenceStoreSpec[ModelSnapshotStore]("/dbfiles/domain.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool) = new ModelSnapshotStore(dbPool)

  val person1ModelFqn = ModelFqn("people", "person1")
  val person2ModelFqn = ModelFqn("people", "person2")
  val nonExistingModelFqn = ModelFqn("people", "noPerson")

  val df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  val p1Snapshot0Date = df.parse("2015-10-20 10:00:00").getTime
  val p1Snapshot10Date = df.parse("2015-10-20 11:00:00").getTime
  val p1Snapshot20Date = df.parse("2015-10-20 12:00:00").getTime

  "A ModelSnapshotStore" when {
    "when creating a snapshot" must {
      "be able to get the snapshot that was created" in withPersistenceStore { store =>
        val version = 2L
        val timestamp = Instant.now()

        val created = SnapshotData(
          SnapshotMetaData(person1ModelFqn, version, timestamp),
          JObject("key" -> JNull))

        store.createSnapshot(created)

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
        metaData(0).version shouldBe 0
        metaData(1).version shouldBe 10
        metaData(2).version shouldBe 20
      }

      "correctly limit the number of results with no offset" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(person1ModelFqn, Some(2), None).success.value
        metaData.length shouldBe 2
        metaData(0).version shouldBe 0
        metaData(1).version shouldBe 10
      }

      "correctly limit the number of results with an offset" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(person1ModelFqn, Some(2), Some(1)).success.value
        metaData.length shouldBe 2
        metaData(0).version shouldBe 10
        metaData(1).version shouldBe 20
      }

      "correctly offset the results with no limit" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(person1ModelFqn, None, Some(1)).success.value
        metaData.length shouldBe 2
        metaData(0).version shouldBe 10
        metaData(1).version shouldBe 20
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
        metaDataList(0).version shouldBe 0
        metaDataList(1).version shouldBe 10
        metaDataList(2).version shouldBe 20
      }

      "return all snapshots with all encopmasing time bounds and no limit-offset" in withPersistenceStore { store =>
        val metaDataList = store.getSnapshotMetaDataForModelByTime(
          person1ModelFqn, Some(p1Snapshot0Date), Some(p1Snapshot20Date), None, None).success.value
        metaDataList.length shouldBe 3
        metaDataList(0).version shouldBe 0
        metaDataList(1).version shouldBe 10
        metaDataList(2).version shouldBe 20
      }
    }

    "when getting the latest snapshot for a model" must {
      "return the correct meta data for a model with snapshots" in withPersistenceStore { store =>
        val metaData = store.getLatestSnapshotMetaDataForModel(person1ModelFqn).success.value
        metaData.value.version shouldBe 20L
      }

      "return None when the specified model does not exist" in withPersistenceStore { store =>
        val queried = store.getSnapshot(nonExistingModelFqn, 0L).success.value
        queried shouldBe None
      }
    }

    "when getting the closest snapshot to a version for a model" must {
      "return the higher version when it is the closest" in withPersistenceStore { store =>
        val snapshotData = store.getClosestSnapshotByVersion(person1ModelFqn, 18).success.value
        snapshotData.value.metaData.version shouldBe 20L
      }

      "return the lower version when it is the closest" in withPersistenceStore { store =>
        val snapshotData = store.getClosestSnapshotByVersion(person1ModelFqn, 14).success.value
        snapshotData.value.metaData.version shouldBe 10L
      }

      "return the higher version when the requested version is equidistant from a higerh and lower snapshot" in withPersistenceStore { store =>
        val snapshotData = store.getClosestSnapshotByVersion(person1ModelFqn, 15).success.value
        snapshotData.value.metaData.version shouldBe 20L
      }
    }
  }
}