package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object UserApiKeyClass extends OrientDbClass {
  val ClassName = "UserApiKey"
  
  object Fields {
    val User = "user"
    val Name = "name"
    val Key = "key"
    val Enabled = "enabled"
    val LastUsed = "lastUsed"
  }
  
  object Indices {
    val Key = "UserApiKey.key"
    val UserName = "UserApiKey.user_name"
  }
}
