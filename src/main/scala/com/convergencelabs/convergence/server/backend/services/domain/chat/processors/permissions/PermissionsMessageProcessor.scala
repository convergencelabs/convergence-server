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

import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.{ChatPermissionsRequest, CommonErrors, UnauthorizedError, UnknownError}
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatPermissions.ChatPermission
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import grizzled.slf4j.Logging

import scala.util.{Success, Try}

trait PermissionsMessageProcessor[M <: ChatPermissionsRequest[R], R] extends Logging {

  def process(hasPermission: (DomainUserId, String, ChatPermission) => Try[Boolean],
              requiredPermission: ChatPermission,
              message: M,
              handleRequest: (M, String) => Try[R],
              createErrorReply: CommonErrors => R
             ): R = {
    hasPermission(message.requester.userId, message.chatId, requiredPermission).flatMap {
      case true =>
        for {
          response <- handleRequest(message, message.chatId)
        } yield {
          response
        }
      case false =>
        Success(createErrorReply(UnauthorizedError()))
    }
      .recover { cause =>
        error("Unexpected error handling chat permissions request", cause)
        createErrorReply(UnknownError())
      }.get
  }
}
