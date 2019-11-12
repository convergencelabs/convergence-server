/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object ModelPermissionsClass extends OrientDbClass {
  val ClassName = "ModelPermissions"

  object Fields {
    val Read = "read"
    val Write = "write"
    val Remove = "remove"
    val Manage = "manage"
  }
}
