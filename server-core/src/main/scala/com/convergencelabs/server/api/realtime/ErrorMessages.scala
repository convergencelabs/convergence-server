package com.convergencelabs.server.api.realtime

import io.convergence.proto.common.ErrorMessage

object ErrorMessages {
  def Unauthorized(message: String): ErrorMessage = {
    ErrorMessage("unauthorized", message, Map())
  }
}