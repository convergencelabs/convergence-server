package com.convergencelabs.server.db.data

case class ImportScript(
  users: Option[List[CreateConvergenceUser]],
  domains: Option[List[CreateDomain]])

case class CreateConvergenceUser(
  username: String,
  password: String,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  displayName: Option[String])

case class CreateDomain(
  namespace: String,
  domainId: String,
  displayName: String,
  status: String,
  statusMessage: String,
  owner: String,
  dbName: String,
  dbUsername: String,
  dbPassword: String,
  dbAdminUsername: String,
  dbAdminPassword: String)
