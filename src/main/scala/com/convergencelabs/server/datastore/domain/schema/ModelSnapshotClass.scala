/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object ModelSnapshotClass extends OrientDbClass {
  val ClassName = "ModelSnapshot"

  object Indices {
    val Id = "Model.id"
  }

  object Fields {
    val Model = "model"
    val Version = "version"
    val Timestamp = "timestamp"
    val Data = "data"
  }
}
