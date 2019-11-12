/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object UserReconnectTokenClass extends OrientDbClass {
  val ClassName = "UserReconnectToken"

  object Indices {
    val Token = "UserReconnectToken.token"
  }

  object Fields {
    val Token = "token"
    val User = "user"
    val ExpireTime = "expireTime"
  }
}
