package com.convergencelabs.server.datastore.domain.schema

object ModelClass extends OrientDBClass {
  val ClassName = "Model"

  object Indices {
    val Id = "Model.id"
    val CollectionId = "Model.collection_id"
  }

  object Fields {
    val Id = "id"
    val Collection = "collection"
    val Version = "version"
    val CreatedTime = "createdTime"
    val ModifiedTime = "modifiedTime"
    val Data = "data"
    val OverridePermissions = "overridePermissions"
    val WorldPermissions = "worldPermissions"
    val UserPermissions = "userPermissions"
    val ValuePrefix = "valuePrefix"
  }
}
