package com.convergencelabs.server.db.data

case class ConvergenceScript(
  users: Option[List[CreateConvergenceUser]],
  domains: Option[List[CreateDomain]])

case class CreateConvergenceUser(
  username: String,
  password: SetPassword,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  displayName: Option[String])

case class CreateDomain(
  id: String,
  namespace: String,
  displayName: String,
  status: String,
  statusMessage: String,
  owner: String,
  dataImport: Option[DomainScript])
