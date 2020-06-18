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

package com.convergencelabs.convergence.server.domain.chat.processors.general

import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.datastore.domain.PermissionsStore.ChatPermissionTarget
import com.convergencelabs.convergence.server.datastore.domain.{ChatStore, PermissionsStore}
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{RemoveChatRequest, RemoveChatResponse}
import com.convergencelabs.convergence.server.domain.chat.ChatPermissions.ChatPermission
import com.convergencelabs.convergence.server.domain.chat.processors.{MessageReplyTask, ReplyAndBroadcastTask}
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatPermissions}
import grizzled.slf4j.Logging

import scala.util.{Success, Try}

object RemoveChatMessageProcessor extends Logging {
  def execute(message: RemoveChatRequest,
              checkPermissions: (DomainUserId, ChatPermission) => Try[Boolean],
              chatStore: ChatStore,
              permissionsStore: PermissionsStore,
             ): ReplyAndBroadcastTask[RemoveChatResponse] = {
    val RemoveChatRequest(_, chatId, requester, _) = message

    (for {
      allowed <- checkPermissions(requester, ChatPermissions.Permissions.RemoveChat)
      response <- if (allowed) {
        removeChat(chatStore, permissionsStore, message.chatId).map { _ =>
          ReplyAndBroadcastTask(MessageReplyTask(message.replyTo, ChatActor.RemoveChatResponse(Right(Ok()))), Some(ChatClientActor.ChatRemoved(chatId)))
        }
      } else {
        val r = ReplyAndBroadcastTask(MessageReplyTask(message.replyTo, ChatActor.RemoveChatResponse(Left(ChatActor.UnauthorizedError()))), None)
        Success(r)
      }
    } yield response)
      .recover { cause =>
        error("Unexpected error removing chat", cause)
        ReplyAndBroadcastTask(MessageReplyTask(message.replyTo, ChatActor.RemoveChatResponse(Left(ChatActor.UnknownError()))), None)
      }.get
  }

  def removeChat(chatStore: ChatStore, permissionStore: PermissionsStore, chatId: String): Try[Unit] = {
    for {
      _ <- permissionStore.removeAllPermissionsForTarget(ChatPermissionTarget(chatId))
      _ <- chatStore.removeChat(chatId)
    } yield ()
  }
}
