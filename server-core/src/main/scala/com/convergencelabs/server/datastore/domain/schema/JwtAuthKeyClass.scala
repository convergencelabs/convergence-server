package com.convergencelabs.server.datastore.domain.schema

object JwtAuthKeyClass extends OrientDBClass {
  val ClassName = "JwtAuthKey"
  
  object Indices {
    val Id = "JwtAuthKey.id"
  }
  
  object Fields {
    val Id = "id"
    val Name = "name"
    val Description = "description"
    val Updated = "updated"
    val Key = "key"
    val Enabled = "enabled"
  }
}
