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
  }

  case class ErrorResponse(error: String) extends AbstractErrorResponse

  type RestResponse = (StatusCode, ResponseMessage)

  val OkResponse: RestResponse = (StatusCodes.OK, SuccessRestResponse())
  val CreateRestResponse: RestResponse = (StatusCodes.Created, SuccessRestResponse())

  val InternalServerError: RestResponse = (StatusCodes.InternalServerError, ErrorResponse("internal_server_error"))
  val DuplicateError: RestResponse = (StatusCodes.Conflict, ErrorResponse("duplicate_error"))
  val InvalidValueError: RestResponse = (StatusCodes.BadRequest, ErrorResponse("invalid_value_error"))
  val NotFoundError: RestResponse = (StatusCodes.NotFound, ErrorResponse("not_found_error"))
  val AuthFailureError: RestResponse = (StatusCodes.Unauthorized, ErrorResponse("unauthorized"))
}
