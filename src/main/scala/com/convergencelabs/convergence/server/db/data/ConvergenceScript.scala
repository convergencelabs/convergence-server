/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.db.data

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
