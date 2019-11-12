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

package com.convergencelabs.convergence.server.api.rest.domain

import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest.JsonSupport
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, Permissions}

import scala.concurrent.ExecutionContext

class DomainRestService(executionContext: ExecutionContext, defaultTimeout: Timeout) extends JsonSupport {

  implicit val ec: ExecutionContext = executionContext
  implicit val t: Timeout = defaultTimeout

  // Permission Checks

  def canAccessDomain(domainFqn: DomainId, authProfile: AuthorizationProfile): Boolean = {
    checkPermission(domainFqn, authProfile, Set(Permissions.Domain.Access))
  }

  def canManageSettings(domainFqn: DomainId, authProfile: AuthorizationProfile): Boolean = {
    checkPermission(domainFqn, authProfile, Set(Permissions.Domain.ManageSettings))
  }

  def canManageUsers(domainFqn: DomainId, authProfile: AuthorizationProfile): Boolean = {
    checkPermission(domainFqn, authProfile, Set(Permissions.Domain.ManageUsers))
  }

  def checkPermission(domainFqn: DomainId, authProfile: AuthorizationProfile, permission: Set[String]): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Global.ManageDomains) ||
      authProfile.hasNamespacePermission(Permissions.Namespace.ManageDomains, domainFqn.namespace) ||
      permission.forall(p => authProfile.hasDomainPermission(p, domainFqn))
  }
}
