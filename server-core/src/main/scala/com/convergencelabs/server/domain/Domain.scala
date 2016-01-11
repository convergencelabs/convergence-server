package com.convergencelabs.server.domain

case class Domain(
  id: String,
  domainFqn: DomainFqn,
  displayName: String,
  dbUsername: String,
  dbPassword: String)
