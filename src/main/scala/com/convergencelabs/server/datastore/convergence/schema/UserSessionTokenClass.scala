package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object UserSessionTokenClass extends OrientDbClass {
  val ClassName = "UserSessionToken"
  
  object Fields {
    val User = "user"
    val Token = "token"
    val ExpiresAt = "expiresAt"
  }
  
  object Indices {
    val Token = "UserSessionToken.token"
  }
}
