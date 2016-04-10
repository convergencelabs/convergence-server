package com.convergencelabs.server.domain

case class Domain(
  id: String,
  domainFqn: DomainFqn,
  displayName: String,
  owner: String)

case class DomainDatabaseInfo(
    database: String,
    username: String,
    password: String)