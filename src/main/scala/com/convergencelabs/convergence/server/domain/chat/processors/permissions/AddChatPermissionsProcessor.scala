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

import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.datastore.domain.PermissionsStore
import com.convergencelabs.convergence.server.datastore.domain.PermissionsStore.ChatPermissionTarget
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{AddChatPermissionsRequest, AddChatPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.domain.chat.{ChatPermissionResolver, ChatPermissions, GroupPermissions, UserPermissions}
import grizzled.slf4j.Logging

import scala.util.Try

object AddChatPermissionsProcessor extends PermissionsMessageProcessor[AddChatPermissionsRequest, AddChatPermissionsResponse] with Logging {

  def execute(message: AddChatPermissionsRequest,
              permissionsStore: PermissionsStore): AddChatPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      hasPermission = ChatPermissionResolver.hasPermissions(permissionsStore.userHasPermissionForTarget),
      handleRequest = updatePermissions(permissionsStore),
      createErrorReply = v => AddChatPermissionsResponse(Left(v))
    )
  }

  def updatePermissions(permissionsStore: PermissionsStore)(message: AddChatPermissionsRequest, chatId: String): Try[AddChatPermissionsResponse] = {
    val AddChatPermissionsRequest(_, _, _, world, user, group, _) = message
    (for {
      _ <- toTry(world) {
        permissionsStore.addPermissionsForWorld(_, ChatPermissionTarget(chatId))
      }
      _ <- unsafeToTry(user) {
        _.foreach { case UserPermissions(userId, permissions) =>
          permissionsStore.addPermissionsForUser(permissions, userId, ChatPermissionTarget(chatId)).get
        }
      }
      _ <- unsafeToTry(group) {
        _.foreach { case GroupPermissions(group, permissions) =>
          permissionsStore.addPermissionsForGroup(permissions, group, ChatPermissionTarget(chatId)).get
        }
      }
    } yield {
      AddChatPermissionsResponse(Right(Ok()))
    }).recover { cause =>
      error("Unexpected error adding chat permissions", cause)
      AddChatPermissionsResponse(Left(UnknownError()))
    }
  }
}
