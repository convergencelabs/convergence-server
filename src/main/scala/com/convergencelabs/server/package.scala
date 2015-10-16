package com.convergencelabs

package object server {
  case class ErrorMessage(code: String, message: String)
  case object SuccessResponse
  case class ProtocolConfiguration(
    defaultMessageTimeout: Long)
}