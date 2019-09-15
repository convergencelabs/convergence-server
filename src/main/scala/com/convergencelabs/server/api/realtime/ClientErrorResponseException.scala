package com.convergencelabs.server.api.realtime

case class ClientErrorResponseException(code: String, message: String) extends Exception(message)
