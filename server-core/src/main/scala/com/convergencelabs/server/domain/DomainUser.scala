package com.convergencelabs.server.domain

case class DomainUser(
  username: String,
  firstName: Option[String],
  lastName: Option[String],
  displayName: Option[String],
  email: Option[String])
