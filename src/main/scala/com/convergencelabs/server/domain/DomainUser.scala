package com.convergencelabs.server.domain

object DomainUserType extends Enumeration {
  type DomainUserType = Value
  val Normal: DomainUserType = Value("normal")
  val Anonymous: DomainUserType = Value("anonymous")
  val Convergence: DomainUserType = Value("convergence")

  def withNameOpt(s: String): Option[Value] = values.find(_.toString.toLowerCase() == s.toLowerCase())
}

object DomainUserId {
  def apply(userType: String, username: String): DomainUserId = DomainUserId(DomainUserType.withName(userType), username)

  def normal(username: String) = DomainUserId(DomainUserType.Normal, username)

  def convergence(username: String) = DomainUserId(DomainUserType.Convergence, username)
}

case class DomainUserId(
                         userType: DomainUserType.Value,
                         username: String) {
  def isConvergence: Boolean = this.userType == DomainUserType.Convergence

  def isNormal: Boolean = this.userType == DomainUserType.Normal

  def isAnonymous: Boolean = this.userType == DomainUserType.Anonymous
}

object DomainUser {
  def apply(userId: DomainUserId,
            firstName: Option[String],
            lastName: Option[String],
            displayName: Option[String],
            email: Option[String]): DomainUser =
    DomainUser(userId.userType, userId.username, firstName, lastName, displayName, email, disabled = false, deleted = false, None)

  def apply(userId: DomainUserId,
            firstName: Option[String],
            lastName: Option[String],
            displayName: Option[String],
            email: Option[String],
            disabled: Boolean,
            deleted: Boolean,
            deletedUsername: Option[String]): DomainUser = DomainUser(userId.userType, userId.username, firstName, lastName, displayName, email, disabled, deleted, deletedUsername)

  def apply(userType: DomainUserType.Value,
            username: String,
            firstName: Option[String],
            lastName: Option[String],
            displayName: Option[String],
            email: Option[String]): DomainUser =
    DomainUser(userType, username, firstName, lastName, displayName, email, disabled = false, deleted = false, None)
}

case class DomainUser(userType: DomainUserType.Value,
                      username: String,
                      firstName: Option[String],
                      lastName: Option[String],
                      displayName: Option[String],
                      email: Option[String],
                      disabled: Boolean,
                      deleted: Boolean,
                      deletedUsername: Option[String]) {

  def toUserId: DomainUserId = DomainUserId(this.userType, this.username)
}

case class DomainUserSessionId(sessionId: String,
                               userId: DomainUserId)
