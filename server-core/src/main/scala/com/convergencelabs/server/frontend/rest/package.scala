package com.convergencelabs.server.frontend

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes

package object rest {

  trait ResponseMessage {
    def ok: Boolean
  }

  abstract class AbstractSuccessResponse() extends ResponseMessage {
    val ok = true
  }

  case class SuccessRestResponse() extends AbstractSuccessResponse

  abstract class AbstractErrorResponse() extends ResponseMessage {
    val ok = false
    def error_code: String
    def error_message: Option[String]
    def error_details: Option[Map[String, Any]]
  }

  case class ErrorResponse(
    error_code: String,
    error_message: Option[String] = None,
    error_details: Option[Map[String, Any]] = None) extends AbstractErrorResponse

  type RestResponse = (StatusCode, ResponseMessage)

  val OkResponse: RestResponse = (StatusCodes.OK, SuccessRestResponse())
  val CreateRestResponse: RestResponse = (StatusCodes.Created, SuccessRestResponse())
  val InternalServerError: RestResponse = (StatusCodes.InternalServerError, ErrorResponse("internal_server_error"))
  val AuthFailureError: RestResponse = (StatusCodes.Unauthorized, ErrorResponse("unauthorized"))
  val ForbiddenError: RestResponse = (StatusCodes.Forbidden, ErrorResponse("forbidden"))
  val MalformedRequestContent: RestResponse = (StatusCodes.BadRequest, ErrorResponse("malformed_request_content"))

  def duplicateResponse(field: String): RestResponse = (StatusCodes.Conflict, ErrorResponse("duplicate_error", None, Some(Map("field" -> field))))
  def invalidValueResponse(field: String): RestResponse = (StatusCodes.BadRequest, ErrorResponse("invalid_value_error", None, Some(Map("field" -> field))))
  def notFoundResponse(message: Option[String] = None): RestResponse = (StatusCodes.NotFound, ErrorResponse("not_found", message))
}
