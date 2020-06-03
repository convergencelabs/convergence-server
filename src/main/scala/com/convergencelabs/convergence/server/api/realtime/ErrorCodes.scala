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

package com.convergencelabs.convergence.server.api.realtime

object ErrorCodes extends Enumeration {
  type ErrorCode  = Value

  val Unauthorized: ErrorCode = Value("unauthorized")

  val GroupNotFound: ErrorCode = Value("group_not_found")
  val UserNotFound: ErrorCode = Value("user_not_found")

  val ActivityAlreadyJoined: ErrorCode = Value("activity_already_joined")

  val ModelUnknownResourceId: ErrorCode = Value("unknown_resource_id")

  val ModelNotFound: ErrorCode = Value("model_not_found")

  val ModelNotOpen: ErrorCode = Value("model_not_open")

  val ModelAlreadyOpen: ErrorCode = Value("model_already_open")

  val ModelAlreadyOpening: ErrorCode = Value("model_already_opening")

  val ModelAlreadyExists: ErrorCode = Value("model_already_exists")

  val ModelClientDataRequestFailure: ErrorCode = Value("data_request_failure")

  val ModelClosingAfterError: ErrorCode = Value("model_closing_after_error")

  val ModelInvalidQuery: ErrorCode = Value("invalid_query")

  val ModelDeleted: ErrorCode = Value("model_deleted")


  val ChatNotFound: ErrorCode = Value("chat_not_found")

  val ChatNotJoined: ErrorCode = Value("chat_not_joined")

  val ChatAlreadyJoined: ErrorCode = Value("chat_already_joined")

  val ChatAlreadyExists: ErrorCode = Value("chat_already_exists")

  val InvalidChatMessage: ErrorCode = Value("invalid_chat_message")




  val Unknown: ErrorCode = Value("unknown")
}
