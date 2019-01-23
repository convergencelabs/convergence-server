package com.convergencelabs.server.frontend

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
    error_code: String,
    error_message: Option[String] = None,
    error_details: Option[Map[String, Any]] = None)
    
  object ErrorResponse {
    def apply(
      error_code: String,
      error_message: Option[String] = None,
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

  def duplicateResponse(field: String): RestResponse = (StatusCodes.Conflict, ErrorResponse("duplicate_error", None, Some(Map("field" -> field))))
  def invalidValueResponse(field: String): RestResponse = (StatusCodes.BadRequest, ErrorResponse("invalid_value_error", None, Some(Map("field" -> field))))
  def notFoundResponse(message: Option[String] = None): RestResponse = (StatusCodes.NotFound, ErrorResponse("not_found", message))
  
  def okResponse(data: Any): RestResponse = (StatusCodes.OK, SuccessRestResponse(data))
  def createdResponse(data: Any): RestResponse = (StatusCodes.Created, SuccessRestResponse(data))
  def response(code: StatusCode, data: Any): RestResponse = (code, SuccessRestResponse(data))
  
  case class PagedRestResponse(data: List[Any], startIndex: Int, totalResults: Int)
}
