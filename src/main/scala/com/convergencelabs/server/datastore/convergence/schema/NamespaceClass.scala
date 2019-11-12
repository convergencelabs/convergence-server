/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object NamespaceClass extends OrientDbClass {
  val ClassName = "Namespace"
  
  object Fields {
    val Id = "id"
    val DisplayName = "displayName"
    val UserNamespace = "userNamespace"
  }
  
  object Indices {
    val Id = "Namespace.id"
    val DisplayName = "Namespace.displayName"
  }
}
