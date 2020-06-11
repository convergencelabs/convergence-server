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

package com.convergencelabs.convergence.server.domain.chat.processors.permissions

import com.convergencelabs.convergence.server.datastore.domain.PermissionsStore
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{SetChatPermissionsRequest, SetChatPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.domain.chat.{ChatPermissionResolver, ChatPermissions, GroupPermissions, UserPermissions}
import com.orientechnologies.orient.core.id.ORID
import grizzled.slf4j.Logging

import scala.util.Try

object SetChatPermissionsProcessor extends PermissionsMessageProcessor[SetChatPermissionsRequest, SetChatPermissionsResponse] with Logging {

  def execute(message: SetChatPermissionsRequest,
              getChatRid: String => Try[ORID],
              permissionsStore: PermissionsStore): SetChatPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      getChatRid= getChatRid,
      hasPermission = ChatPermissionResolver.hasPermissions(getChatRid, permissionsStore.hasPermission),
      handleRequest = updatePermissions(permissionsStore),
      createErrorReply = v => SetChatPermissionsResponse(Left(v))
    )
  }

  def updatePermissions(permissionsStore: PermissionsStore)(message: SetChatPermissionsRequest, chatRid: ORID): Try[SetChatPermissionsResponse] = {
    val SetChatPermissionsRequest(_, _, _, world, user, group, _) = message
    (for {
      _ <- toTry(world) {
        permissionsStore.setWorldPermissions(_, Some(chatRid))
      }
      _ <- unsafeToTry(user) {
        _.foreach { case UserPermissions(userId, permissions) =>
          permissionsStore.setUserPermissions(permissions, userId, Some(chatRid)).get
        }
      }
      _ <- unsafeToTry(group) {
        _.foreach { case GroupPermissions(group, permissions) =>
          permissionsStore.setGroupPermissions(permissions, group, Some(chatRid)).get
        }
      }
    } yield {
      SetChatPermissionsResponse(Right(()))
    }).recover { cause =>
      error("Unexpected error setting chat permissions", cause)
      SetChatPermissionsResponse(Left(UnknownError()))
    }
  }
}
