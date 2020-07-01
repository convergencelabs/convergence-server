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

package com.convergencelabs.convergence.server.backend.services.domain.chat.processors.permissions


import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{ChatPermissionTarget, PermissionsStore}
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.{GetUserChatPermissionsRequest, GetUserChatPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatPermissionResolver, ChatPermissions}
import grizzled.slf4j.Logging

import scala.util.Try

object GetUserChatPermissionsProcessor extends PermissionsMessageProcessor[GetUserChatPermissionsRequest, GetUserChatPermissionsResponse] with Logging {

  def execute(message: GetUserChatPermissionsRequest,
              permissionsStore: PermissionsStore): GetUserChatPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      hasPermission = ChatPermissionResolver.hasPermissions(permissionsStore.userHasPermission),
      handleRequest = getPermissions(permissionsStore),
      createErrorReply = v => GetUserChatPermissionsResponse(Left(v))
    )
  }

  def getPermissions(permissionsStore: PermissionsStore)(message: GetUserChatPermissionsRequest, chatId: String): Try[GetUserChatPermissionsResponse] = {
    permissionsStore.getPermissionsForUser(message.userId, ChatPermissionTarget(chatId))
      .map(permissions => GetUserChatPermissionsResponse(Right(permissions)))
      .recover { cause =>
        error("Unexpected error getting chat user permissions", cause)
        GetUserChatPermissionsResponse(Left(UnknownError()))
      }
  }
}
