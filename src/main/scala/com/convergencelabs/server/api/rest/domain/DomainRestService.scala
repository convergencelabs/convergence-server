package com.convergencelabs.server.api.rest.domain

import akka.util.Timeout
import com.convergencelabs.server.api.rest.JsonSupport
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.security.{AuthorizationProfile, Permissions}

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
