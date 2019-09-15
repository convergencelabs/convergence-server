package com.convergencelabs.server.security

object Permissions {
  object Global {
    val Access = "access"
    val ManageDomains = "manage-domains"
    val ManageSettings = "manage-settings"
    val ManageUsers= "manage-users"
  }
  
  object Namespace {
    val Access = "namespace-access"
    val ManageDomains = "namesapce-manage-domains"
    val ManageUsers= "namesapce-manage-users"
  }
  
  object Domain {
    val Access = "domain-access"
    val ManageSettings = "domain-manage-settings"
    val ManageUsers= "domain-manage-users"
  }
}
