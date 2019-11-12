/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain

object DomainStatus extends Enumeration {
  type DomainStatus = Value
  val Initializing: DomainStatus = Value("initializing")
  val Error: DomainStatus = Value("error")
  val Online: DomainStatus = Value("online")
  val Offline: DomainStatus = Value("offline")
  val Maintenance: DomainStatus = Value("maintenance")
  val Deleting: DomainStatus = Value("deleting")
}

case class Domain(
  domainFqn: DomainId,
  displayName: String,
  status: DomainStatus.Value,
  statusMessage: String)

case class DomainDatabase(
  database: String,
  username: String,
  password: String,
  adminUsername: String,
  adminPassword: String)
