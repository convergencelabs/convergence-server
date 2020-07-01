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

package com.convergencelabs.convergence.server.model.domain.user

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration


/**
 * The [[DomainUserId]] represents the system wide unique id of a domain user.
 *
 * @param userType The type of the user.
 * @param username The unique username (within that type)
 */
final case class DomainUserId(@JsonScalaEnumeration(classOf[DomainUserTypeReference]) userType: DomainUserType.DomainUserType,
                              username: String) {
  @JsonIgnore
  def isConvergence: Boolean = this.userType == DomainUserType.Convergence

  @JsonIgnore
  def isNormal: Boolean = this.userType == DomainUserType.Normal

  @JsonIgnore
  def isAnonymous: Boolean = this.userType == DomainUserType.Anonymous
}

object DomainUserId {
  /**
   * Creates a new domain user id for the given username and the type parsed
   * from a string. The type must be a valid string for the [[DomainUserType]]
   * Enumeration.
   *
   * @param userType The string representation of the type of the user
   * @param username The username of the user.
   * @return A DomainUserId with the type and username specified.
   */
  def apply(userType: String, username: String): DomainUserId = DomainUserId(DomainUserType.withName(userType), username)

  /**
   * Creates a normal domain user id.
   *
   * @param username The username of the normal user.
   * @return A DomainUserId with the specified username and a normal type.
   */
  def normal(username: String): DomainUserId = DomainUserId(DomainUserType.Normal, username)

  /**
   * Creates a convergence domain user id.
   *
   * @param username The username of the convergence user.
   * @return A DomainUserId with the specified username and a convergence type.
   */
  def convergence(username: String): DomainUserId = DomainUserId(DomainUserType.Convergence, username)

  /**
   * Creates a anonymous domain user id.
   *
   * @param username The username of the anonymous user.
   * @return A DomainUserId with the specified username and a anonymous type.
   */
  def anonymous(username: String): DomainUserId = DomainUserId(DomainUserType.Anonymous, username)
}
