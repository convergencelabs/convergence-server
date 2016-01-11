package com.convergencelabs.server.domain

case class DomainUser(
  uid: String,
  username: String,
  firstName: Option[String],
  lastName: Option[String],
  email: Option[String])
