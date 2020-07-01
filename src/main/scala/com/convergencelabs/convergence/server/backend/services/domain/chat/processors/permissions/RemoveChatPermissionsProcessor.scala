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

import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.PermissionsStore
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.ChatPermissionTarget
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.{RemoveChatPermissionsRequest, RemoveChatPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatPermissionResolver, ChatPermissions}
import grizzled.slf4j.Logging

import scala.util.Try

object RemoveChatPermissionsProcessor extends PermissionsMessageProcessor[RemoveChatPermissionsRequest, RemoveChatPermissionsResponse] with Logging {

  def execute(message: RemoveChatPermissionsRequest,
              permissionsStore: PermissionsStore): RemoveChatPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      hasPermission = ChatPermissionResolver.hasPermissions(permissionsStore.userHasPermission),
      handleRequest = updatePermissions(permissionsStore),
      createErrorReply = v => RemoveChatPermissionsResponse(Left(v))
    )
  }

  def updatePermissions(permissionsStore: PermissionsStore)(message: RemoveChatPermissionsRequest, chatId: String): Try[RemoveChatPermissionsResponse] = {
    val RemoveChatPermissionsRequest(_, _, _, world, user, group, _) = message
    val target = ChatPermissionTarget(chatId)
    permissionsStore
      .removePermissionsForTarget(target, user.getOrElse(Set.empty), group.getOrElse(Set.empty), world.getOrElse(Set.empty))
      .map(_ => RemoveChatPermissionsResponse(Right(Ok())))
      .recover { cause =>
        error("Unexpected error removing chat permissions", cause)
        RemoveChatPermissionsResponse(Left(UnknownError()))
      }
  }
}
