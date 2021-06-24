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
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.{ResolveSessionPermissionsRequest, ResolveSessionPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatPermissionResolver, ChatPermissions}
import grizzled.slf4j.Logging

import scala.util.Try

object GetClientChatPermissionsProcessor extends PermissionsMessageProcessor[ResolveSessionPermissionsRequest, ResolveSessionPermissionsResponse] with Logging {

  def execute(message: ResolveSessionPermissionsRequest,
              permissionsStore: PermissionsStore): ResolveSessionPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      hasPermission = ChatPermissionResolver.hasPermissions(permissionsStore.userHasPermission),
      handleRequest = getPermissions(permissionsStore),
      createErrorReply = v => ResolveSessionPermissionsResponse(Left(v))
    )
  }

  def getPermissions(permissionsStore: PermissionsStore)(message: ResolveSessionPermissionsRequest, chatId: String): Try[ResolveSessionPermissionsResponse] = {
    val ResolveSessionPermissionsRequest(_, _, requester, _) = message
    permissionsStore
      .resolveUserPermissionsForTarget(requester.userId, ChatPermissionTarget(chatId), ChatPermissions.AllExistingChatPermissions)
      .map(p => ResolveSessionPermissionsResponse(Right(p)))
      .recover { cause =>
        error("Unexpected error getting client chat permissions", cause)
        ResolveSessionPermissionsResponse(Left(UnknownError()))
      }
  }
}
