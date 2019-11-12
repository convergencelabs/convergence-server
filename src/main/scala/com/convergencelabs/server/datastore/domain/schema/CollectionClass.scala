/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object CollectionClass extends OrientDbClass {
  val ClassName = "Collection"

  object Indices {
    val Id = "Collection.id"
  }

  object Fields {
    val Id = "id"
    val Description = "description"
    val OverrideSnapshotConfig = "overrideSnapshotConfig"
    val SnapshotConfig = "snapshotConfig"
    val WorldPermissions = "worldPermissions"
    val UserPermissions = "userPermissions"
  }
}
