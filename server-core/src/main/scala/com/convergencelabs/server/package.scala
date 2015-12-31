package com.convergencelabs


import scala.concurrent.duration.FiniteDuration
package object server {
  case class ErrorResponse(code: String, message: String)
  case class ProtocolConfiguration(defaultRequestTimeout: FiniteDuration)
}
