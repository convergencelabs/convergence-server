/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object UserGroupClass extends OrientDbClass {
  val ClassName = "UserGroup"

  object Indices {
    val Id = "UserGroup.id"
  }

  object Fields {
    val Id = "id"
    val Description = "description"
    val Members = "members"
  }
}
