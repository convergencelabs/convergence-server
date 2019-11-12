/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object ModelOperationClass extends OrientDbClass {
  val ClassName = "ModelOperation"
  
  object Indices {
    val Model = "ModelOperation.model"
    val Model_Version = "ModelOperation.model_version"
  }
  
  object Fields {
    val Model = "model"
    val Version = "version"
    val Timestamp = "timestamp"
    val User = "user"
    val Session = "session"
    val Operation = "operation"
  }
}
