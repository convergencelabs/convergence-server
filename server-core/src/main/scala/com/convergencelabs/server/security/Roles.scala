package com.convergencelabs.server.security

object Roles {
  object Global {
    val Developer = "Developer"
    val DomainAdmin = "Domain Admin"
    val ServerAdmin = "Server Admin"
  }
  
  object Namespace {
    val Developer = "Developer"
    val DomainAdmin = "Domain Admin"
    val Owner = "Owner"
  }
  
  object Domain {
    val Developer = "Developer"
    val DomainAdmin = "Domain Admin"
    val Owner = "Owner"
  }
}
