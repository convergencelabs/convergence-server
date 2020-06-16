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

import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.datastore.domain.ChatEvent
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{GetChatHistoryRequest, GetChatHistoryResponse, UnauthorizedError, UnknownError}
import com.convergencelabs.convergence.server.domain.chat.ChatPermissions
import com.convergencelabs.convergence.server.domain.chat.ChatPermissions.ChatPermission
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import grizzled.slf4j.Logging

import scala.util.{Success, Try}

object GetHistoryMessageProcessor extends Logging {

  type GetChatHistory = (String, Option[Set[String]], Option[Long], QueryOffset, QueryLimit, Option[Boolean], Option[String]) => Try[PagedData[ChatEvent]]

  def execute(message: GetChatHistoryRequest,
              getHistory: GetChatHistory,
              checkPermissions: (DomainUserId, ChatPermission) => Try[Boolean]): GetChatHistoryResponse = {
    val GetChatHistoryRequest(_, chatId, requester, offset, limit, startEvent, forward, eventTypes, messageFilter, _) = message

    (for {
      allowed <- requester
        .map(r => checkPermissions(r.userId, ChatPermissions.Permissions.JoinChat))
        .getOrElse(Success(true))
      response <- if (allowed) {
        getHistory(chatId, eventTypes, startEvent, offset, limit, forward, messageFilter)
          .map(r => GetChatHistoryResponse(Right(r)))
      } else {
        Success(GetChatHistoryResponse(Left(UnauthorizedError())))
      }
    } yield response).recover { cause =>
      logger.error("Unexpected error getting chat history", cause)
      GetChatHistoryResponse(Left(UnknownError()))
    }.get
  }
}
