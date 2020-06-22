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

import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

/**
 * A [[DomainUser]] represents an individual user inside a Domain.
 *
 * @param userType        The type of user indicating if they are a normal user,
 * @param username        The username of the user.
 * @param firstName       The user's first name or None if not set.
 * @param lastName        The user's last name or None if not set.
 * @param displayName     The user's display name or None if not set.
 * @param email           The user's email address, or None if not set.
 * @param lastLogin       The time this user last logged in, or None if they
 *                        have never logged in.
 * @param disabled        True if the user is disabled, false otherwise.
 * @param deleted         True if the user has been deleted, false otherwise.
 * @param deletedUsername The username of the user before they were deleted or
 *                        None if the user has not been deleted yet.
 */
final case class DomainUser(@JsonScalaEnumeration(classOf[DomainUserTypeReference]) userType: DomainUserType.Value,
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