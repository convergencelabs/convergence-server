package com.convergencelabs.server.domain

object DomainStatus extends Enumeration {
  val Initializing = Value("initializing");
  val Error = Value("error");
  val Online = Value("online");
  val Offline = Value("offline");
  val Maintenance = Value("maintenance");
  val Terminiating = Value("terminating");
}

case class Domain(
  id: String,
  domainFqn: DomainFqn,
  displayName: String,
  owner: String,
  status: DomainStatus.Value)

case class DomainDatabaseInfo(
  database: String,
  username: String,
  password: String)
