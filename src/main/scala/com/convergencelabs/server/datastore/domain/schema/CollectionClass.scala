package com.convergencelabs.server.datastore.domain.schema

object CollectionClass extends OrientDbClass {
  val ClassName = "Collection"

  object Indices {
    val Id = "Collection.id"
  }

  object Fields {
    val Id = "id"
    val Description = "description"
    val OverrideSnapshotConfig = "overrideSnapshotConfig"
    val SnapshotConfig = "snapshotConfig"
    val WorldPermissions = "worldPermissions"
    val UserPermissions = "userPermissions"
  }
}
