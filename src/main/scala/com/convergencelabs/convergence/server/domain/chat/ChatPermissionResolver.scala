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

package com.convergencelabs.convergence.server.domain.chat

import com.convergencelabs.convergence.server.datastore.domain.{ChatStore, PermissionsStore}
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatPermissions.ChatPermission
import com.orientechnologies.orient.core.id.ORID

import scala.util.{Success, Try}

object ChatPermissionResolver {

  def hasPermissions(chatStore: ChatStore,
                     permissionsStore: PermissionsStore,
                     chatId: String)(userId: DomainUserId, permission: ChatPermission): Try[Boolean] = {
    hasChatPermissions(chatStore, permissionsStore, chatId, permission, userId)
  }

  def hasPermissions(chatStore: ChatStore,
                     permissionsStore: PermissionsStore,
                     chatId: String,
                     permission: ChatPermission)(userId: DomainUserId): Try[Boolean] = {
    hasChatPermissions(chatStore, permissionsStore, chatId, permission, userId)
  }

  def hasChatPermissions(chatStore: ChatStore,
                         permissionsStore: PermissionsStore,
                         chatId: String,
                         permission: ChatPermission,
                         userId: DomainUserId): Try[Boolean] = {
    if (userId.isConvergence) {
      Success(true)
    } else {
      for {
        chatRid <- chatStore.getChatRid(chatId)
        hasPermission <- permissionsStore.hasPermissionForRecord(userId, chatRid, permission.p)
      } yield hasPermission
    }
  }

  def hasPermissions(getChatRid: String => Try[ORID],
                     hasPermission: (DomainUserId, ORID, String) => Try[Boolean]
                    )
                    (
                      userId: DomainUserId,
                      chatId: String,
                      permission: ChatPermission
                    ): Try[Boolean] = {
    if (userId.isConvergence) {
      Success(true)
    } else {
      for {
        chatRid <- getChatRid(chatId)
        hasPermission <- hasPermission(userId, chatRid, permission.p)
      } yield hasPermission
    }
  }
}
