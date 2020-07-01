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

package com.convergencelabs.convergence.server.backend.services.domain.chat

import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{ChatPermissionTarget, PermissionsStore}
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatPermissions.ChatPermission
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId

import scala.util.{Success, Try}

private[chat] object ChatPermissionResolver {

  def hasPermissions(permissionsStore: PermissionsStore,
                     chatId: String)(userId: DomainUserId, permission: ChatPermission): Try[Boolean] = {
    hasChatPermissions(permissionsStore, chatId, permission, userId)
  }

  def hasPermissions(permissionsStore: PermissionsStore,
                     chatId: String,
                     permission: ChatPermission)(userId: DomainUserId): Try[Boolean] = {
    hasChatPermissions(permissionsStore, chatId, permission, userId)
  }

  def hasChatPermissions(permissionsStore: PermissionsStore,
                         chatId: String,
                         permission: ChatPermission,
                         userId: DomainUserId): Try[Boolean] = {
    if (userId.isConvergence) {
      Success(true)
    } else {
      for {
        hasPermission <- permissionsStore.userHasPermission(userId, ChatPermissionTarget(chatId), permission.p)
      } yield hasPermission
    }
  }

  def hasPermissions(hasPermission: (DomainUserId, ChatPermissionTarget, String) => Try[Boolean])
                    ( userId: DomainUserId,
                      chatId: String,
                      permission: ChatPermission
                    ): Try[Boolean] = {
    if (userId.isConvergence) {
      Success(true)
    } else {
      for {
        hasPermission <- hasPermission(userId, ChatPermissionTarget(chatId), permission.p)
      } yield hasPermission
    }
  }
}
