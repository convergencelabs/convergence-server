/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

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
