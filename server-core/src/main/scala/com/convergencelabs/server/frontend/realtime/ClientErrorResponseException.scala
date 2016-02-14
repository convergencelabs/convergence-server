package com.convergencelabs.server.frontend.realtime

case class ClientErrorResponseException(code: String, message: String) extends Exception(message)
