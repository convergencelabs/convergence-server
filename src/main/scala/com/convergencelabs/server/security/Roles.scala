/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.security

object Roles {
  object Server {
    val Developer = "Developer"
    val DomainAdmin = "Domain Admin"
    val ServerAdmin = "Server Admin"
  }
  
  object Namespace {
    val Developer = "Developer"
    val DomainAdmin = "Domain Admin"
    val Owner = "Owner"
  }
  
  object Domain {
    val Developer = "Developer"
    val DomainAdmin = "Domain Admin"
    val Owner = "Owner"
  }
}
