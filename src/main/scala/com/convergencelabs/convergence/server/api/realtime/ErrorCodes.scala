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

/**
 * The ErrorCodes enumeration defines the set of error strings that
 * can be sent back to the client.
 */
object ErrorCodes extends Enumeration {
  type ErrorCode  = Value

  //
  // Common Error Codes
  //
  val Unknown: ErrorCode = Value("unknown")

  val Timeout: ErrorCode = Value("timeout")

  val Unauthorized: ErrorCode = Value("unauthorized")

  val MalformedRequestContent: ErrorCode = Value("malformed_request_content")

  val NotSupported: ErrorCode = Value("not_supported")

  //
  // Identity Related Errors
  //
  val GroupNotFound: ErrorCode = Value("group_not_found")
  val UserNotFound: ErrorCode = Value("user_not_found")

  //
  // Activity Errors
  //
  val ActivityAlreadyJoined: ErrorCode = Value("activity_already_joined")

  val ActivityNotJoined: ErrorCode = Value("activity_not_joined")

  //
  // Model Errors
  //
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

  //
  // Chat Related Errors
  //
  val ChatNotFound: ErrorCode = Value("chat_not_found")

  val ChatNotJoined: ErrorCode = Value("chat_not_joined")

  val ChatAlreadyJoined: ErrorCode = Value("chat_already_joined")

  val ChatAlreadyMember: ErrorCode = Value("chat_already_a_member")

  val NotAlreadyMember: ErrorCode = Value("not_a_member")

  val CantRemoveSelf: ErrorCode = Value("cant_remove_self")

  val ChatAlreadyExists: ErrorCode = Value("chat_already_exists")
}
