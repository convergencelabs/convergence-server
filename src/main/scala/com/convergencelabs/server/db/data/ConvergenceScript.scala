/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.db.data

case class ConvergenceScript(
  users: Option[List[CreateConvergenceUser]],
  domains: Option[List[CreateDomain]])

case class CreateConvergenceUser(
  username: String,
  password: SetPassword,
  bearerToken: String,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  displayName: Option[String])

case class CreateDomain(
  id: String,
  namespace: String,
  displayName: String,
  status: String,
  statusMessage: String,
  dataImport: Option[DomainScript])
