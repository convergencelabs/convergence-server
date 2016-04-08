package com.convergencelabs.server.frontend

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes

package object rest {

  trait ResponseMessage {
    def ok: Boolean
  }

  case class ErrorResponse(ok: Boolean, error: String) extends ResponseMessage

  object ErrorResponse {
    def apply(error: String): ErrorResponse = {
      ErrorResponse(false, error)
    }
  }
  
  type RestResponse = Tuple2[StatusCode, ResponseMessage]
  
  val InternalServerError: RestResponse = (StatusCodes.InternalServerError, ErrorResponse("Internal Server Error!"))

}