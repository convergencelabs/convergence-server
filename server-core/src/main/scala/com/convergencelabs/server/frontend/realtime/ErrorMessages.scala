package com.convergencelabs.server.frontend.realtime

object ErrorMessages {
  def Unauthorized(message: String): ErrorMessage = {
    ErrorMessage("unauthorized", message, Map())
  }
}