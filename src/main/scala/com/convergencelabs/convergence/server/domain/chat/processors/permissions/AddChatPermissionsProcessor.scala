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
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{AddChatPermissionsRequest, AddChatPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.domain.chat.processors.MessageReplyTask
import com.convergencelabs.convergence.server.domain.chat.{ChatPermissionResolver, ChatPermissions, GroupPermissions, UserPermissions}
import com.orientechnologies.orient.core.id.ORID
import grizzled.slf4j.Logging

import scala.util.Try

object AddChatPermissionsProcessor extends PermissionsMessageProcessor[AddChatPermissionsRequest, AddChatPermissionsResponse] with Logging {

  def execute(message: AddChatPermissionsRequest,
              getChatRid: String => Try[ORID],
              permissionsStore: PermissionsStore): AddChatPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      getChatRid= getChatRid,
      hasPermission = ChatPermissionResolver.hasPermissions(getChatRid, permissionsStore.hasPermissionForRecord),
      handleRequest = updatePermissions(permissionsStore),
      createErrorReply = v => AddChatPermissionsResponse(Left(v))
    )
  }

  def updatePermissions(permissionsStore: PermissionsStore)(message: AddChatPermissionsRequest, chatRid: ORID): Try[AddChatPermissionsResponse] = {
    val AddChatPermissionsRequest(_, _, _, world, user, group, _) = message
    (for {
      _ <- toTry(world) {
        permissionsStore.addWorldPermissions(_, Some(chatRid))
      }
      _ <- unsafeToTry(user) {
        _.foreach { case UserPermissions(userId, permissions) =>
          permissionsStore.addUserPermissions(permissions, userId, Some(chatRid)).get
        }
      }
      _ <- unsafeToTry(group) {
        _.foreach { case GroupPermissions(group, permissions) =>
          permissionsStore.addGroupPermissions(permissions, group, Some(chatRid)).get
        }
      }
    } yield {
      AddChatPermissionsResponse(Right(()))
    }).recover { cause =>
      error("Unexpected error adding chat permissions", cause)
      AddChatPermissionsResponse(Left(UnknownError()))
    }
  }
}
