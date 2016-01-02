package com.convergencelabs.server.util.concurrent

case class UnexpectedErrorException(code: String, details: String) extends Exception(details) {
  def this() = {
    this("unknown", "An unkown error occured")
  }
}
