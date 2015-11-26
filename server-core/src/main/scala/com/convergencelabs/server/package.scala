package com.convergencelabs

package object server {
  case class ErrorResponse(code: String, message: String)
  case class ProtocolConfiguration(
    defaultMessageTimeout: Long)
}