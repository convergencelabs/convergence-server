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
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.{GetClientChatPermissionsRequest, GetClientChatPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatPermissionResolver, ChatPermissions}
import grizzled.slf4j.Logging

import scala.util.Try

object GetClientChatPermissionsProcessor extends PermissionsMessageProcessor[GetClientChatPermissionsRequest, GetClientChatPermissionsResponse] with Logging {

  def execute(message: GetClientChatPermissionsRequest,
              permissionsStore: PermissionsStore): GetClientChatPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      hasPermission = ChatPermissionResolver.hasPermissions(permissionsStore.userHasPermission),
      handleRequest = getPermissions(permissionsStore),
      createErrorReply = v => GetClientChatPermissionsResponse(Left(v))
    )
  }

  def getPermissions(permissionsStore: PermissionsStore)(message: GetClientChatPermissionsRequest, chatId: String): Try[GetClientChatPermissionsResponse] = {
    val GetClientChatPermissionsRequest(_, _, requester, _) = message
    permissionsStore
      .getAggregateUserPermissionsForTarget(requester.userId, ChatPermissionTarget(chatId), ChatPermissions.AllExistingChatPermissions)
      .map(p => GetClientChatPermissionsResponse(Right(p)))
      .recover { cause =>
        error("Unexpected error getting client chat permissions", cause)
        GetClientChatPermissionsResponse(Left(UnknownError()))
      }
  }
}
