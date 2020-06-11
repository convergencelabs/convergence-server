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
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{GetGroupChatPermissionsRequest, GetGroupChatPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.domain.chat.{ChatPermissionResolver, ChatPermissions}
import com.orientechnologies.orient.core.id.ORID
import grizzled.slf4j.Logging

import scala.util.Try

object GetGroupChatPermissionsProcessor extends PermissionsMessageProcessor[GetGroupChatPermissionsRequest, GetGroupChatPermissionsResponse] with Logging {

  def execute(message: GetGroupChatPermissionsRequest,
              getChatRid: String => Try[ORID],
              permissionsStore: PermissionsStore): GetGroupChatPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      getChatRid = getChatRid,
      hasPermission = ChatPermissionResolver.hasPermissions(getChatRid, permissionsStore.hasPermissionForRecord),
      handleRequest = getPermissions(permissionsStore),
      createErrorReply = v => GetGroupChatPermissionsResponse(Left(v))
    )
  }

  def getPermissions(permissionsStore: PermissionsStore)(message: GetGroupChatPermissionsRequest, chatRid: ORID): Try[GetGroupChatPermissionsResponse] = {
    permissionsStore.getGroupPermissions(message.groupId, Some(chatRid))
      .map(permissions => GetGroupChatPermissionsResponse(Right(permissions)))
      .recover { cause =>
        error("Unexpected error getting chat group permissions", cause)
        GetGroupChatPermissionsResponse(Left(UnknownError()))
      }
  }
}
