package com.convergencelabs

import scala.concurrent.duration.FiniteDuration

package object server {
  case class ErrorResponse(code: String, message: String)

  case class HeartbeatConfiguration(
    enabled: Boolean,
    pingInterval: FiniteDuration,
    pongTimeout: FiniteDuration)

  case class ProtocolConfiguration(
    defaultRequestTimeout: FiniteDuration,
    heartbeatConfig: HeartbeatConfiguration)
}
