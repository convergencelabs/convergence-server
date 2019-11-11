package com.convergencelabs.server.api.realtime

import com.convergencelabs.convergence.proto.core._

object ErrorMessages {
  def Unauthorized(message: String): ErrorMessage = {
    ErrorMessage("unauthorized", message, Map())
  }
}