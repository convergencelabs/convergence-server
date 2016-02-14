package com.convergencelabs.server.util.concurrent

case class UnexpectedErrorException(details: String) extends Exception(details)
