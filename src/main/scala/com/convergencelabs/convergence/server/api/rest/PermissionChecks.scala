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

package com.convergencelabs.convergence.server.api.rest

import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, Permissions, Roles}

trait PermissionChecks {
  protected def isServerAdmin(authProfile: AuthorizationProfile): Boolean = {
    authProfile.hasServerRole(Roles.Server.ServerAdmin)
  }

  protected def canManageDomainsInNamespace(namespace: String, authProfile: AuthorizationProfile): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Server.ManageDomains) ||
      authProfile.hasNamespacePermission(Permissions.Namespace.ManageDomains, namespace)
  }

  protected def canAccessDomain(domainFqn: DomainId, authProfile: AuthorizationProfile): Boolean = {
    checkDomainPermission(domainFqn, authProfile, Set(Permissions.Domain.Access))
  }

  protected def canManageDomain(domainFqn: DomainId, authProfile: AuthorizationProfile): Boolean = {
    checkDomainPermission(domainFqn, authProfile, Set(Permissions.Domain.ManageSettings))
  }

  protected def checkDomainPermission(domainFqn: DomainId, authProfile: AuthorizationProfile, permission: Set[String]): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Server.ManageDomains) ||
      authProfile.hasNamespacePermission(Permissions.Namespace.ManageDomains, domainFqn.namespace) ||
      permission.forall(p => authProfile.hasDomainPermission(p, domainFqn))
  }
}
