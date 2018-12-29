package com.convergencelabs.server.frontend.realtime

import io.convergence.proto.connection.ErrorMessage

object ErrorMessages {
  def Unauthorized(message: String): ErrorMessage = {
    ErrorMessage("unauthorized", message, Map())
  }
}