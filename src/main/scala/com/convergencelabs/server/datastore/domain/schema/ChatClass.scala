/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object ChatClass extends OrientDbClass {
  val ClassName = "Chat"

  object Fields {
    val Id = "id"
    val Type = "type"
    val Created = "created"
    val Private = "private"
    val Name = "name"
    val Topic = "topic"
    val Members = "members"
    val Permissions = "permissions"
  }

  object Indices {
    val Id = "Chat.id"
  }
}
