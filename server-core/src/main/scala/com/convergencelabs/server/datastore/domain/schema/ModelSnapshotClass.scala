package com.convergencelabs.server.datastore.domain.schema

object ModelSnapshotClass extends OrientDbClass {
  val ClassName = "ModelSnapshot"

  object Indices {
    val Id = "Model.id"
  }

  object Fields {
    val Model = "model"
    val Version = "version"
    val Timestamp = "timestamp"
    val Data = "data"
  }
}
