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

package com.convergencelabs.convergence.server.api.rest

sealed trait RestResponseEntity {
  def ok: Boolean
  def body: Any
}

sealed abstract class AbstractSuccessResponseEntity() extends RestResponseEntity {
  val ok = true
}

final case class SuccessRestResponseEntity(body: Any = ()) extends AbstractSuccessResponseEntity

sealed abstract class AbstractErrorResponseEntity() extends RestResponseEntity {
  val ok = false
  override val body: ErrorData
}

final case class ErrorData(
                            error_code:    String,
                            error_message: Option[String]           = None,
                            error_details: Option[Map[String, Any]] = None)

object ErrorResponseEntity {
  def apply(
             error_code:    String,
             error_message: Option[String]           = None,
             error_details: Option[Map[String, Any]] = None): ErrorResponseEntity = ErrorResponseEntity(ErrorData(error_code, error_message, error_details))
}
final case class ErrorResponseEntity(body: ErrorData) extends AbstractErrorResponseEntity
