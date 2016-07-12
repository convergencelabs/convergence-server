package com.convergencelabs.server.domain

import com.convergencelabs.server.User

object DomainStatus extends Enumeration {
  val Initializing = Value("initializing");
  val Error = Value("error");
  val Online = Value("online");
  val Offline = Value("offline");
  val Maintenance = Value("maintenance");
  val Terminiating = Value("terminating");
}

case class Domain(
  domainFqn: DomainFqn,
  displayName: String,
  owner: User,
  status: DomainStatus.Value)

case class DomainDatabaseInfo(
  database: String,
  username: String,
  password: String)
