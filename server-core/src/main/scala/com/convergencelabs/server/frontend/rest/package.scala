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
    def error: String
  }

  case class ErrorResponse(error: String, message: Option[String]) extends AbstractErrorResponse

  
  case class DuplicateError(val field: String) extends AbstractErrorResponse {
    val error = "duplicate_error"
  }
  
  def duplicateResponse(field: String): RestResponse = (StatusCodes.Conflict, DuplicateError(field))
  
  case class InvalidValueError(field: String) extends AbstractErrorResponse {
    val error = "invalid_value_error"
  }
  
  def invalidValueResponse(field: String): RestResponse = (StatusCodes.BadRequest, InvalidValueError(field))
  
  
  type RestResponse = (StatusCode, ResponseMessage)

  val OkResponse: RestResponse = (StatusCodes.OK, SuccessRestResponse())
  val CreateRestResponse: RestResponse = (StatusCodes.Created, SuccessRestResponse())
  val InternalServerError: RestResponse = (StatusCodes.InternalServerError, ErrorResponse("internal_server_error", None))
  val NotFoundError: RestResponse = (StatusCodes.NotFound, ErrorResponse("not_found_error", None))
  val AuthFailureError: RestResponse = (StatusCodes.Unauthorized, ErrorResponse("unauthorized", None))
  val ForbiddenError: RestResponse = (StatusCodes.Forbidden, ErrorResponse("forbidden", None))
  val MalformedRequestContent: RestResponse = (StatusCodes.BadRequest, ErrorResponse("malformed_request_content", None))
}
