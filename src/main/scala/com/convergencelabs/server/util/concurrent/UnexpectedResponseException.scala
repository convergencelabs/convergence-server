package com.convergencelabs.server.util.concurrent

case class UnexpectedResponseException(message: String) extends Exception(message)
