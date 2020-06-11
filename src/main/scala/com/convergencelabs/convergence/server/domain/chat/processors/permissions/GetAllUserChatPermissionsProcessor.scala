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
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{GetWorldChatPermissionsRequest, GetWorldChatPermissionsResponse, UnknownError}
import com.convergencelabs.convergence.server.domain.chat.{ChatPermissionResolver, ChatPermissions}
import com.orientechnologies.orient.core.id.ORID
import grizzled.slf4j.Logging

import scala.util.Try

object GetWorldChatPermissionsProcessor extends PermissionsMessageProcessor[GetWorldChatPermissionsRequest, GetWorldChatPermissionsResponse] with Logging {

  def execute(message: GetWorldChatPermissionsRequest,
              getChatRid: String => Try[ORID],
              permissionsStore: PermissionsStore): GetWorldChatPermissionsResponse = {
    process(
      message = message,
      requiredPermission = ChatPermissions.Permissions.Manage,
      getChatRid = getChatRid,
      hasPermission = ChatPermissionResolver.hasPermissions(getChatRid, permissionsStore.hasPermission),
      handleRequest = getPermissions(permissionsStore),
      createErrorReply = v => GetWorldChatPermissionsResponse(Left(v))
    )
  }

  def getPermissions(permissionsStore: PermissionsStore)(message: GetWorldChatPermissionsRequest, chatRid: ORID): Try[GetWorldChatPermissionsResponse] = {
    permissionsStore.getWorldPermissions(Some(chatRid))
      .map(p => p.map(_.permission))
      .map(p => GetWorldChatPermissionsResponse(Right(p)))
      .recover { cause =>
        error("Unexpected error getting chat world permissions", cause)
        GetWorldChatPermissionsResponse(Left(UnknownError()))
      }
  }
}
