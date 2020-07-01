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

package com.convergencelabs.convergence.server.api.realtime.protocol

import com.convergencelabs.convergence.proto.core.{DomainUserData, DomainUserIdData, DomainUserTypeData}
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId, DomainUserType}

object IdentityProtoConverters {

  def domainUserToProto(user: DomainUser): DomainUserData = {
    val DomainUser(userType, username, firstName, lastName, displayName, email, _, disabled, deleted, deletedUsername) = user
    val userId = DomainUserId(userType, username)
    val userIdData = Some(domainUserIdToProto(userId))
    DomainUserData(userIdData, firstName, lastName, displayName, email, disabled, deleted, deletedUsername)
  }

  def domainUserIdToProto(userId: DomainUserId): DomainUserIdData = {
    val DomainUserId(userType, username) = userId

    val userTypeData = userType match {
      case DomainUserType.Normal => DomainUserTypeData.Normal
      case DomainUserType.Convergence => DomainUserTypeData.Convergence
      case DomainUserType.Anonymous => DomainUserTypeData.Anonymous
    }

    DomainUserIdData(userTypeData, username)
  }

  def protoToDomainUserId(userIdData: DomainUserIdData): DomainUserId = {
    val DomainUserIdData(userTypeData, username, _) = userIdData

    val userType = userTypeData match {
      case DomainUserTypeData.Normal => DomainUserType.Normal
      case DomainUserTypeData.Convergence => DomainUserType.Convergence
      case DomainUserTypeData.Anonymous => DomainUserType.Anonymous
      case _ => ???
    }

    DomainUserId(userType, username)
  }
}
