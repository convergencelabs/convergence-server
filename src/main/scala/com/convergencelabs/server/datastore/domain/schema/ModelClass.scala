/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object ModelClass extends OrientDbClass {
  val ClassName = "Model"

  object Indices {
    val Id = "Model.id"
    val Collection_Id = "Model.collection_id"
  }

  object Fields {
    val Id = "id"
    val Collection = "collection"
    val Version = "version"
    val CreatedTime = "createdTime"
    val ModifiedTime = "modifiedTime"
    val Data = "data"
    val OverridePermissions = "overridePermissions"
    val WorldPermissions = "worldPermissions"
    val UserPermissions = "userPermissions"
    val ValuePrefix = "valuePrefix"
  }
}
