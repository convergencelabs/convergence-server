package com.convergencelabs.server.domain

object DomainUserType extends Enumeration {
  val Normal, Anonymous, Admin = Value
  def withNameOpt(s: String): Option[Value] = values.find(_.toString.toLowerCase() == s.toLowerCase())
}

case class DomainUser(
  userType: DomainUserType.Value,
  username: String,
  firstName: Option[String],
  lastName: Option[String],
  displayName: Option[String],
  email: Option[String])
