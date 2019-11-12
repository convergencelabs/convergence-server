/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.api

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes

package object rest {

  trait ResponseMessage {
    def ok: Boolean
    def body: Any
  }

  abstract class AbstractSuccessResponse() extends ResponseMessage {
    val ok = true
  }

  case class SuccessRestResponse(body: Any = ()) extends AbstractSuccessResponse

  abstract class AbstractErrorResponse() extends ResponseMessage {
    val ok = false
    override val body: ErrorData
  }

  case class ErrorData(
    error_code:    String,
    error_message: Option[String]           = None,
    error_details: Option[Map[String, Any]] = None)

  object ErrorResponse {
    def apply(
      error_code:    String,
      error_message: Option[String]           = None,
      error_details: Option[Map[String, Any]] = None): ErrorResponse = ErrorResponse(ErrorData(error_code, error_message, error_details))
  }
  case class ErrorResponse(body: ErrorData) extends AbstractErrorResponse

  type RestResponse = (StatusCode, ResponseMessage)

  val OkResponse: RestResponse = (StatusCodes.OK, SuccessRestResponse())
  val CreatedResponse: RestResponse = (StatusCodes.Created, SuccessRestResponse())
  val InternalServerError: RestResponse = (StatusCodes.InternalServerError, ErrorResponse("internal_server_error"))
  val AuthFailureError: RestResponse = (StatusCodes.Unauthorized, ErrorResponse("unauthorized"))
  val ForbiddenError: RestResponse = (StatusCodes.Forbidden, ErrorResponse("forbidden"))
  val MalformedRequestContent: RestResponse = (StatusCodes.BadRequest, ErrorResponse("malformed_request_content"))

  def duplicateResponse(field: String): RestResponse = (StatusCodes.Conflict, ErrorResponse("duplicate", None, Some(Map("field" -> field))))
  def invalidValueResponse(message: String, field: Option[String]): RestResponse = (
    StatusCodes.BadRequest,
    ErrorResponse("invalid_value", Some(message), field.map(f => Map("field" -> f))))

    
  def notFoundResponse(message: Option[String] = None): RestResponse = (StatusCodes.NotFound, ErrorResponse("not_found", message))
  def methodNotAllowed(methods: Seq[String]): RestResponse = (
    StatusCodes.NotFound,
    ErrorResponse("method_not_allowed", Some(s"The only supported methods at this url are: ${methods mkString ", "}")))
  def unknownErrorResponse(message: Option[String] = None): RestResponse = (StatusCodes.NotFound, ErrorResponse("unknown", message))
  def namespaceNotFoundResponse(namespace: String): RestResponse = (StatusCodes.BadRequest, ErrorResponse("namespace_not_found", None, Some(Map("namespace" -> namespace))))
  
  def okResponse(data: Any): RestResponse = (StatusCodes.OK, SuccessRestResponse(data))
  def createdResponse(data: Any): RestResponse = (StatusCodes.Created, SuccessRestResponse(data))
  def response(code: StatusCode, data: Any): RestResponse = (code, SuccessRestResponse(data))

  case class PagedRestResponse(data: List[Any], startIndex: Long, totalResults: Long)

  case class DomainRestData(
    displayName: String,
    namespace:   String,
    domainId:    String,
    status:      String)
}
