/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object JwtAuthKeyClass extends OrientDbClass {
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
