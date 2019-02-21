package com.convergencelabs.server.domain


object DomainStatus extends Enumeration {
  val Initializing = Value("initializing");
  val Error = Value("error");
  val Online = Value("online");
  val Offline = Value("offline");
  val Maintenance = Value("maintenance");
  val Deleting = Value("deleting");
}

case class Domain(
  domainFqn: DomainFqn,
  displayName: String,
  status: DomainStatus.Value,
  statusMessage: String)

case class DomainDatabase(
  database: String,
  username: String,
  password: String,
  adminUsername: String,
  adminPassword: String)
