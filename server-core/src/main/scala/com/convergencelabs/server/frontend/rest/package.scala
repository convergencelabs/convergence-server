package com.convergencelabs.server.frontend

import akka.http.scaladsl.model.StatusCode

package object rest {

  trait ResponseMessage {
    def ok: Boolean
  }

  case class ErrorResponse(ok: Boolean, error: String) extends ResponseMessage

  type RestResponse = Tuple2[StatusCode, ResponseMessage]
}