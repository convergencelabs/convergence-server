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
import com.convergencelabs.convergence.server.datastore.domain.PermissionsStore.ChatPermissionTarget
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{GetAllGroupChatPermissionsRequest, GetAllGroupChatPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.domain.chat.{ChatPermissionResolver, ChatPermissions}
import grizzled.slf4j.Logging

import scala.util.Try

object GetAllGroupChatPermissionsProcessor extends PermissionsMessageProcessor[GetAllGroupChatPermissionsRequest, GetAllGroupChatPermissionsResponse] with Logging {

  def execute(message: GetAllGroupChatPermissionsRequest,
              permissionsStore: PermissionsStore): GetAllGroupChatPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      hasPermission = ChatPermissionResolver.hasPermissions(permissionsStore.userHasPermission),
      handleRequest = getPermissions(permissionsStore),
      createErrorReply = v => GetAllGroupChatPermissionsResponse(Left(v))
    )
  }

  def getPermissions(permissionsStore: PermissionsStore)(message: GetAllGroupChatPermissionsRequest, chatId: String): Try[GetAllGroupChatPermissionsResponse] = {
    permissionsStore.getGroupPermissionsForTarget(ChatPermissionTarget(chatId))
      .map { permissions =>
        val map = permissions.groupBy { _.group } map { case (group, groupPermissions) => group.id -> groupPermissions.map { _.permission } }
        GetAllGroupChatPermissionsResponse(Right(map))
      }
      .recover { cause =>
        error("Unexpected error getting chat group permissions", cause)
        GetAllGroupChatPermissionsResponse(Left(UnknownError()))
      }
  }
}
