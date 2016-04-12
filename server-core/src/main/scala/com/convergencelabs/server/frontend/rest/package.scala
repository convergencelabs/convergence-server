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

  type RestResponse = Tuple2[StatusCode, ResponseMessage]

  val InternalServerError: RestResponse = (StatusCodes.InternalServerError, ErrorResponse("Internal Server Error!"))
}
