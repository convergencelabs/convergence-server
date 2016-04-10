package com.convergencelabs

import scala.concurrent.duration.FiniteDuration
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit

package object server {
  case class UnknownErrorResponse(details: String)

  case class HeartbeatConfiguration(
    enabled: Boolean,
    pingInterval: FiniteDuration,
    pongTimeout: FiniteDuration)

  case class ProtocolConfiguration(
    handshakeTimeout: FiniteDuration,
    defaultRequestTimeout: FiniteDuration,
    heartbeatConfig: HeartbeatConfiguration)

  object ProtocolConfigUtil {
    def loadConfig(config: Config): ProtocolConfiguration = {
      val protoConfig = config.getConfig("convergence.protocol")
      ProtocolConfiguration(
        FiniteDuration(protoConfig.getInt("handshakeTimeout"), TimeUnit.SECONDS),
        FiniteDuration(protoConfig.getInt("defaultRequestTimeout"), TimeUnit.SECONDS),
        HeartbeatConfiguration(
          protoConfig.getBoolean("heartbeat.enabled"),
          FiniteDuration(protoConfig.getInt("heartbeat.pingInterval"), TimeUnit.SECONDS),
          FiniteDuration(protoConfig.getInt("heartbeat.pongTimeout"), TimeUnit.SECONDS)))
    }
  }
}
