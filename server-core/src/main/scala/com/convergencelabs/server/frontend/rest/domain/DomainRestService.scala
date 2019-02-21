package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.domain.DomainFqn

import akka.actor.ActorRef
import akka.util.Timeout
import com.convergencelabs.server.security.AuthorizationProfile
import com.convergencelabs.server.security.Permissions

class DomainRestService(executionContext: ExecutionContext, defaultTimeout: Timeout) extends JsonSupport {

  import akka.pattern.ask

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  // Permission Checks

  def canAccessDomain(domainFqn: DomainFqn, authProfile: AuthorizationProfile): Boolean = {
    checkPermission(domainFqn, authProfile, Set(Permissions.Domain.Access))
  }

  def canManageSettings(domainFqn: DomainFqn, authProfile: AuthorizationProfile): Boolean = {
    checkPermission(domainFqn, authProfile, Set(Permissions.Domain.ManageSettings))
  }

  def canManageUsers(domainFqn: DomainFqn, authProfile: AuthorizationProfile): Boolean = {
    checkPermission(domainFqn, authProfile, Set(Permissions.Domain.ManageUsers))
  }

  def checkPermission(domainFqn: DomainFqn, authProfile: AuthorizationProfile, permission: Set[String]): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Global.ManageDomains) ||
      authProfile.hasNamespacePermission(Permissions.Namespace.ManageDomains, domainFqn.namespace) ||
      permission.forall(p => authProfile.hasDomainPermission(p, domainFqn))
  }
}
