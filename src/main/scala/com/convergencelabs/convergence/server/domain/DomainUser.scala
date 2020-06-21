/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain

import java.time.Instant

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

case class DomainUserId(userType: DomainUserType.Value, username: String) {
  def isConvergence: Boolean = this.userType == DomainUserType.Convergence

  def isNormal: Boolean = this.userType == DomainUserType.Normal

  def isAnonymous: Boolean = this.userType == DomainUserType.Anonymous
}

object DomainUser {
  def apply(userId: DomainUserId,
            firstName: Option[String],
            lastName: Option[String],
            displayName: Option[String],
            email: Option[String],
            lastLogin: Option[Instant]): DomainUser =
    DomainUser(userId.userType, userId.username, firstName, lastName, displayName, email, lastLogin, disabled = false, deleted = false, None)

  def apply(userId: DomainUserId,
            firstName: Option[String],
            lastName: Option[String],
            displayName: Option[String],
            email: Option[String],
            lastLogin: Option[Instant],
            disabled: Boolean,
            deleted: Boolean,
            deletedUsername: Option[String]): DomainUser =
    DomainUser(userId.userType, userId.username, firstName, lastName, displayName, email, lastLogin, disabled, deleted, deletedUsername)

  def apply(userType: DomainUserType.Value,
            username: String,
            firstName: Option[String],
            lastName: Option[String],
            displayName: Option[String],
            email: Option[String],
            lastLogin: Option[Instant]): DomainUser =
    DomainUser(userType, username, firstName, lastName, displayName, email, lastLogin, disabled = false, deleted = false, None)
}

case class DomainUser(userType: DomainUserType.Value,
                      username: String,
                      firstName: Option[String],
                      lastName: Option[String],
                      displayName: Option[String],
                      email: Option[String],
                      lastLogin: Option[Instant],
                      disabled: Boolean,
                      deleted: Boolean,
                      deletedUsername: Option[String]) {

  def toUserId: DomainUserId = DomainUserId(this.userType, this.username)
}

case class DomainUserSessionId(sessionId: String,
                               userId: DomainUserId)
