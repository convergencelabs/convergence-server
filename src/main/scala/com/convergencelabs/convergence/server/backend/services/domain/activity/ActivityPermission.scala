/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.activity


object ActivityPermission {
  object Constants {
    val Join: String = "join"
    val Manage: String = "manage"
    val SetState: String = "set_state"
    val ViewState: String = "view_state"

    val AllActivityPermissions = Set(Join, Manage, SetState, ViewState)
  }

  object Permissions {
    val Join: ActivityPermission = ActivityPermission(Constants.Join)
    val Manage: ActivityPermission = ActivityPermission(Constants.Manage)
    val SetState: ActivityPermission = ActivityPermission(Constants.SetState)
    val ViewState: ActivityPermission = ActivityPermission(Constants.ViewState)
  }

  def of(permission: String): Option[ActivityPermission] = {
    permission match {
      case Constants.Join => Some(Permissions.Join)
      case Constants.Manage => Some(Permissions.Manage)
      case Constants.SetState => Some(Permissions.SetState)
      case Constants.ViewState => Some(Permissions.ViewState)
      case _ => None
    }
  }
}

final case class ActivityPermission private (permission: String)
