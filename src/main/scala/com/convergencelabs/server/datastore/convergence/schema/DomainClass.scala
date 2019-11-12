/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass


object DomainClass extends OrientDbClass {
  val ClassName = "Domain"
  
  object Fields {
    val Id = "id"
    val Namespace = "namespace"
    val DisplayName = "displayName"
    val Status = "status"
    val StatusMessage = "statusMessage"
    val DatabaseName = "databaseName"
    val DatabaseUsername = "databaseUsername"
    val DatabasePassword = "databasePassword"
    val DatabaseAdminUsername = "databaseAdminUsername"
    val DatabaseAdminPassword = "databaseAdminPassword"
  }
  
  object Eval {
    val NamespaceId = "namespace.id"
  }
  
  object Indices {
    val NamespaceId = "Domain.namespace_id"
    val DatabaseName = "Domain.databaseName"
  }
}
