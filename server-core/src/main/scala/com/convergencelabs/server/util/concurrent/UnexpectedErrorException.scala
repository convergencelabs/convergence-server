package com.convergencelabs.server.util.concurrent

case class UnexpectedErrorException(code: String, message: String) extends Exception(message) {
  def this() = {
    this("unknown", "An unkown error occured")
  }
}
