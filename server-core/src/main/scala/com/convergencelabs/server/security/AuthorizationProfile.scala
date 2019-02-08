package com.convergencelabs.server.security

import com.convergencelabs.server.datastore.convergence.DomainRoleTarget
import com.convergencelabs.server.datastore.convergence.GlobalRoleTarget
import com.convergencelabs.server.datastore.convergence.NamespaceRoleTarget
import com.convergencelabs.server.datastore.convergence.RoleStore.UserRoles
import com.convergencelabs.server.datastore.convergence.RoleTarget
import com.convergencelabs.server.domain.DomainFqn

class AuthorizationProfile(val username: String, userRoles: UserRoles) extends Serializable {

  private[this] val rolesByTarget: Map[RoleTarget, Set[String]] = userRoles.roles.groupBy( userRole => userRole.target).map {
    case (target, userRole) => (target, userRole.map(_.role.name))
  }
  
  private[this] val permissionsByTarget: Map[RoleTarget, Set[String]] = userRoles.roles.groupBy( userRole => userRole.target).map {
    case (target, role) => (target, role.flatten(_.role.permissions))
  }
  
  def hasRoleForTarget(role: String, target: RoleTarget): Boolean = {
    rolesByTarget.exists { case (t, r) => target == t && r.contains(role) }
  }
  
  def hasPermissionForTarget(permission: String, target: RoleTarget): Boolean = {
    permissionsByTarget.exists { case (t, p) => target == t && p.contains(permission) }
  }
  
  def hasGlobalPermission(permission: String): Boolean = {
    hasPermissionForTarget(permission, GlobalRoleTarget)
  }
  
  def hasNamespacePermission(permission: String, namespaceId: String): Boolean = {
    hasPermissionForTarget(permission, NamespaceRoleTarget(namespaceId))
  }
  
  def hasDomainPermission(permission: String, namespaceId: String, domainId: String): Boolean = {
    hasPermissionForTarget(permission, DomainRoleTarget(DomainFqn(namespaceId, domainId)))
  }
  
  def hasDomainPermission(permission: String, domainFqn: DomainFqn): Boolean = {
    hasPermissionForTarget(permission, DomainRoleTarget(domainFqn))
  }
}