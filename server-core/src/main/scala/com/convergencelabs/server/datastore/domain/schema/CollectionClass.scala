package com.convergencelabs.server.datastore.domain.schema

object CollectionClass extends OrientDBClass {
  val ClassName = "Collection"

  object Indices {
    val Id = "Collection.id"
  }

  object Fields {
    val Id = "id"
    val Name = "name"
    val OverrideSnapshotConfig = "overrideSnapshotConfig"
    val SnapshotConfig = "snapshotConfig"
    val WorldPermissions = "worldPermissions"
    val UserPermissions = "userPermissions"
  }
}
