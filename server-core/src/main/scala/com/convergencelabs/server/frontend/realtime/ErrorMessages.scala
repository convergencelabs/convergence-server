package com.convergencelabs.server.frontend.realtime

import convergence.protocol.connection.ErrorMessage

object ErrorMessages {
  def Unauthorized(message: String): ErrorMessage = {
    ErrorMessage("unauthorized", message, Map())
  }
}