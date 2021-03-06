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

package com.convergencelabs.convergence.server.security

import com.convergencelabs.convergence.server.backend.datastore.convergence.RoleStore.UserRoles
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.role
import com.convergencelabs.convergence.server.model.server.role.{DomainRoleTarget, NamespaceRoleTarget, RoleTarget, ServerRoleTarget}

object AuthorizationProfile {
  def apply(username: String, userRoles: UserRoles): AuthorizationProfile = {
     new AuthorizationProfile(AuthorizationProfileData(username, userRoles))
  }

  def apply(data: AuthorizationProfileData): AuthorizationProfile = {
    new AuthorizationProfile(data)
  }
}

final class AuthorizationProfile(val data: AuthorizationProfileData) extends Serializable {

  val username: String = data.username
  val userRoles: UserRoles = data.userRoles

  private[this] val rolesByTarget: Map[RoleTarget, Set[String]] = userRoles.roles.groupBy( userRole => userRole.target).map {
    case (target, userRole) => (target, userRole.map(_.role.name))
  }
  
  private[this] val permissionsByTarget: Map[RoleTarget, Set[String]] = userRoles.roles.groupBy( userRole => userRole.target).map {
    case (target, role) => (target, role.flatten(_.role.permissions))
  }

  def hasServerRole(role: String): Boolean = {
    hasRoleForTarget(role, ServerRoleTarget())
  }

  def getServerRole(): Option[String] = {
    val roles = rolesByTarget.get(ServerRoleTarget())
    roles.map(_.head)
  }
  
  def hasRoleForTarget(role: String, target: RoleTarget): Boolean = {
    rolesByTarget.exists { case (t, r) => target == t && r.contains(role) }
  }
  
  def hasPermissionForTarget(permission: String, target: RoleTarget): Boolean = {
    permissionsByTarget.exists { case (t, p) => target == t && p.contains(permission) }
  }
  
  def hasGlobalPermission(permission: String): Boolean = {
    hasPermissionForTarget(permission, ServerRoleTarget())
  }
  
  def hasNamespacePermission(permission: String, namespaceId: String): Boolean = {
    hasPermissionForTarget(permission, NamespaceRoleTarget(namespaceId))
  }
  
  def hasDomainPermission(permission: String, namespaceId: String, domainId: String): Boolean = {
    hasPermissionForTarget(permission, DomainRoleTarget(DomainId(namespaceId, domainId)))
  }
  
  def hasDomainPermission(permission: String, domainFqn: DomainId): Boolean = {
    hasPermissionForTarget(permission, role.DomainRoleTarget(domainFqn))
  }
}