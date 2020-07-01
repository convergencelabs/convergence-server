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
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.{GetWorldChatPermissionsRequest, GetWorldChatPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatPermissionResolver, ChatPermissions}
import grizzled.slf4j.Logging

import scala.util.Try

object GetWorldChatPermissionsProcessor extends PermissionsMessageProcessor[GetWorldChatPermissionsRequest, GetWorldChatPermissionsResponse] with Logging {

  def execute(message: GetWorldChatPermissionsRequest,
              permissionsStore: PermissionsStore): GetWorldChatPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      hasPermission = ChatPermissionResolver.hasPermissions(permissionsStore.userHasPermission _),
      handleRequest = getPermissions(permissionsStore),
      createErrorReply = v => GetWorldChatPermissionsResponse(Left(v))
    )
  }

  def getPermissions(permissionsStore: PermissionsStore)(message: GetWorldChatPermissionsRequest, chatId: String): Try[GetWorldChatPermissionsResponse] = {
    permissionsStore.getPermissionsForWorld(ChatPermissionTarget(chatId))
      .map(p => p.map(_.permission))
      .map(p => GetWorldChatPermissionsResponse(Right(p)))
      .recover { cause =>
        error("Unexpected error getting chat world permissions", cause)
        GetWorldChatPermissionsResponse(Left(UnknownError()))
      }
  }
}
