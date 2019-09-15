package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object UserFavoriteDomainClass extends OrientDbClass {
  val ClassName = "UserFavoriteDomain"
  
  object Fields {
    val User = "user"
    val Domain = "token"
  }
  
  object Indices {
    val UserDomain = "UserFavoriteDomain.user_domain"
    val User = "UserFavoriteDomain.user"
    val Domain = "UserFavoriteDomain.domain"
  }
}
