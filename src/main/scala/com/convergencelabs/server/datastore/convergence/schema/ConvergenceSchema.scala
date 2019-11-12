/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence.schema

object ConvergenceSchema {
  object Classes {
    val Config = ConfigClass
    val ConvergenceDelta = ConvergenceDeltaClass
    val ConvergenceDeltaHistory = ConvergenceDeltaHistoryClass
    val Domain = DomainClass
    val DomainDelta = DomainDeltaClass
    val DomainDeltaHistory = DomainDeltaHistoryClass
    val Namespace = NamespaceClass
    val Permission = PermissionClass
    val Role = RoleClass
    val User = UserClass
    val UserApiKey = UserApiKeyClass
    val UserRole = UserRoleClass
    val UserSessionToken = UserSessionTokenClass
  }

  object Sequences {

  }
}
