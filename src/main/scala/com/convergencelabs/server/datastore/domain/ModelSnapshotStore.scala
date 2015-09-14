package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.domain.model.ModelFqn
import org.json4s.JsonAST.JValue

trait ModelSnapshotStore {

  def addSnapshot(snapshotData: SnapshotData): Unit

  def removeSnapshot(fqn: ModelFqn, version: Long): Unit

  def removeAllSnapshotsForModel(fqn: ModelFqn): Unit

  def removeAllSnapshotsForCollection(collectionId: String): Unit

  def getSnapshots(fqn: ModelFqn, limit: Int, offset: Int): List[SnapshotMetaData]

  def getSnapshotsByTime(fqn: ModelFqn, startTime: Long, endTime: Long): List[SnapshotMetaData]

  def getSnapshot(fqn: ModelFqn, version: Long): SnapshotData

  def getClosestSnapshotByVersion(fqn: ModelFqn, version: Long): SnapshotData

  def getLatestSnapshotMetaData(fqn: ModelFqn): SnapshotMetaData
}

case class SnapshotMetaData(fqn: ModelFqn, version: Long, timestamp: Long)

case class SnapshotData(metaData: SnapshotMetaData, data: JValue)


