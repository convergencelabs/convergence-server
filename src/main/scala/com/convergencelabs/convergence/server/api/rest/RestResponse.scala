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

package com.convergencelabs.convergence.server.api

import akka.http.scaladsl.model.{StatusCode, StatusCodes}

package object rest {

  type RestResponse = (StatusCode, RestResponseEntity)

  val OkResponse: RestResponse = (StatusCodes.OK, SuccessRestResponseEntity())
  val NotFoundResponse: RestResponse = (StatusCodes.NotFound, ErrorResponseEntity("not_found", None))
  val CreatedResponse: RestResponse = (StatusCodes.Created, SuccessRestResponseEntity())
  val DeletedResponse: RestResponse = (StatusCodes.OK, SuccessRestResponseEntity())
  val InternalServerError: RestResponse = (StatusCodes.InternalServerError, ErrorResponseEntity("internal_server_error"))
  val AuthFailureError: RestResponse = (StatusCodes.Unauthorized, ErrorResponseEntity("unauthorized"))
  val ForbiddenError: RestResponse = (StatusCodes.Forbidden, ErrorResponseEntity("forbidden"))
  val MalformedRequestContent: RestResponse = (StatusCodes.BadRequest, ErrorResponseEntity("malformed_request_content"))

  def badRequest(message: String, details: Option[Map[String, Any]] = None): RestResponse = (StatusCodes.BadRequest, ErrorResponseEntity("bad_request", Some(message), details))

  def conflictsResponse(field: String, value: String): RestResponse = (StatusCodes.Conflict, ErrorResponseEntity("conflicts", None, Some(Map(field -> value))))

  def duplicateResponse(field: String): RestResponse = (StatusCodes.Conflict, ErrorResponseEntity("duplicate", None, Some(Map("field" -> field))))
  def invalidValueResponse(message: String, field: Option[String]): RestResponse = (
    StatusCodes.BadRequest,
    ErrorResponseEntity("invalid_value", Some(message), field.map(f => Map("field" -> f))))

  def forbiddenResponse(message: Option[String] = None): RestResponse = (StatusCodes.Forbidden, ErrorResponseEntity("forbidden", message))
  def notFoundResponse(message: String): RestResponse = notFoundResponse(Some(message))
  def notFoundResponse(message: Option[String] = None): RestResponse = (StatusCodes.NotFound, ErrorResponseEntity("not_found", message))
  def methodNotAllowed(methods: Seq[String]): RestResponse = (
    StatusCodes.MethodNotAllowed,
    ErrorResponseEntity("method_not_allowed", Some(s"The only supported methods at this url are: ${methods mkString ", "}")))
  def unknownErrorResponse(message: Option[String] = None): RestResponse = (StatusCodes.InternalServerError, ErrorResponseEntity("unknown", message))
  def namespaceNotFoundResponse(namespace: String): RestResponse = (StatusCodes.NotFound, ErrorResponseEntity("namespace_not_found", None, Some(Map("namespace" -> namespace))))

  def okResponse(data: Any): RestResponse = (StatusCodes.OK, SuccessRestResponseEntity(data))
  def deletedResponse(data: Any): RestResponse = (StatusCodes.Gone, SuccessRestResponseEntity(data))
  def createdResponse(data: Any): RestResponse = (StatusCodes.Created, SuccessRestResponseEntity(data))
  def response(code: StatusCode, data: Any): RestResponse = (code, SuccessRestResponseEntity(data))
}