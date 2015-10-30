package com.convergencelabs.server.datastore.domain

import org.scalatest.WordSpec
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

class ModelSnapshotStoreSpec
    extends WordSpec
    with PersistenceStoreSpec[ModelSnapshotStore] {

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
        assert(queried.get == created)
      }
    }

    "when getting a specific snapshot" must {
      "return the correct snapshot when one exists" in withPersistenceStore { store =>
        val queried = store.getSnapshot(person1ModelFqn, 0L)
        assert(queried.isDefined)
      }

      "return None when the specified version does not exist" in withPersistenceStore { store =>
        val queried = store.getSnapshot(person1ModelFqn, 5L)
        assert(queried.isEmpty)
      }

      "return None when the specified model does not exist" in withPersistenceStore { store =>
        val queried = store.getSnapshot(nonExistingModelFqn, 0L)
        assert(queried.isEmpty)
      }
    }

    "when getting all snapshot meta data" must {
      "return all meta data in proper order when no limit or offest is provided" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(person1ModelFqn, None, None)
        assert(metaData.length == 3)
        assert(metaData(0).version == 0)
        assert(metaData(1).version == 10)
        assert(metaData(2).version == 20)
      }

      "correctly limit the number of results with no offset" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(person1ModelFqn, Some(2), None)
        assert(metaData.length == 2)
        assert(metaData(0).version == 0)
        assert(metaData(1).version == 10)
      }

      "correctly limit the number of results with an offset" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(person1ModelFqn, Some(2), Some(1))
        assert(metaData.length == 2)
        assert(metaData(0).version == 10)
        assert(metaData(1).version == 20)
      }

      "correctly offset the results with no limit" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(person1ModelFqn, None, Some(1))
        assert(metaData.length == 2)
        assert(metaData(0).version == 10)
        assert(metaData(1).version == 20)
      }

      "return an empty list for a non-existent model" in withPersistenceStore { store =>
        val metaData = store.getSnapshotMetaDataForModel(nonExistingModelFqn, None, None)
        assert(metaData.isEmpty)
      }
    }

    "when getting snapshots by time" must {
      "return all snapshots if no time or limit-offset" in withPersistenceStore { store =>
        val metaDataList = store.getSnapshotMetaDataForModelByTime(person1ModelFqn, None, None, None, None)
        assert(metaDataList.length == 3)
        assert(metaDataList(0).version == 0)
        assert(metaDataList(1).version == 10)
        assert(metaDataList(2).version == 20)
      }

      "return all snapshots with all encopmasing time bounds and no limit-offset" in withPersistenceStore { store =>
        val metaDataList = store.getSnapshotMetaDataForModelByTime(
          person1ModelFqn, Some(p1Snapshot0Date), Some(p1Snapshot20Date), None, None)
        assert(metaDataList.length == 3)
        assert(metaDataList(0).version == 0)
        assert(metaDataList(1).version == 10)
        assert(metaDataList(2).version == 20)
      }
    }

    "when getting the latest snapshot for a model" must {
      "return the correct meta data for a model with snapshots" in withPersistenceStore { store =>
        val metaData = store.getLatestSnapshotMetaDataForModel(person1ModelFqn)
        assert(metaData.isDefined)
        assert(metaData.get.version == 20L)
      }

      "return None when the specified model does not exist" in withPersistenceStore { store =>
        val queried = store.getSnapshot(nonExistingModelFqn, 0L)
        assert(queried.isEmpty)
      }
    }
    
    "when getting the closest snapshot to a version for a model" must {
      "return the higher version when it is the closest" in withPersistenceStore { store =>
        val snapshotData = store.getClosestSnapshotByVersion(person1ModelFqn, 18)
        assert(snapshotData.isDefined)
        assert(snapshotData.get.metaData.version == 20L)
      }
      
      "return the lower version when it is the closest" in withPersistenceStore { store =>
        val snapshotData = store.getClosestSnapshotByVersion(person1ModelFqn, 14)
        assert(snapshotData.isDefined)
        assert(snapshotData.get.metaData.version == 10L)
      }
      
      "return the higher version when the requested version is equidistant from a higerh and lower snapshot" in withPersistenceStore { store =>
        val snapshotData = store.getClosestSnapshotByVersion(person1ModelFqn, 15)
        assert(snapshotData.isDefined)
        assert(snapshotData.get.metaData.version == 20L)
      }
    }
  }
}