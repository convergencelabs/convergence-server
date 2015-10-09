package com.convergencelabs

package object server {
  case class ErrorMessage(code: String, message: String)
  case class Success()
  case class ProtocolConfiguration(
    defaultMessageTimeout: Long)
}