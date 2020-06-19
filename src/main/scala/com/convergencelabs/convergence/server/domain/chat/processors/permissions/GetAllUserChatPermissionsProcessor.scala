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
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{GetAllUserChatPermissionsRequest, GetAllUserChatPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.domain.chat.{ChatPermissionResolver, ChatPermissions}
import grizzled.slf4j.Logging

import scala.util.Try

object GetAllUserChatPermissionsProcessor extends PermissionsMessageProcessor[GetAllUserChatPermissionsRequest, GetAllUserChatPermissionsResponse] with Logging {

  def execute(message: GetAllUserChatPermissionsRequest,
              permissionsStore: PermissionsStore): GetAllUserChatPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      hasPermission = ChatPermissionResolver.hasPermissions(permissionsStore.userHasPermission),
      handleRequest = getPermissions(permissionsStore),
      createErrorReply = v => GetAllUserChatPermissionsResponse(Left(v))
    )
  }

  def getPermissions(permissionsStore: PermissionsStore)(message: GetAllUserChatPermissionsRequest, chatId: String): Try[GetAllUserChatPermissionsResponse] = {
    permissionsStore.getUserPermissionsForTarget(ChatPermissionTarget(chatId))
      .map { permissions =>
        val map = permissions.groupBy { _.user } map { case (user, userPermissions) => DomainUserId(user.userType, user.username) -> userPermissions.map { _.permission } }
        GetAllUserChatPermissionsResponse(Right(map))
      }
      .recover { cause =>
        error("Unexpected error getting chat user permissions", cause)
        GetAllUserChatPermissionsResponse(Left(UnknownError()))
      }
  }
}
