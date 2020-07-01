/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.realtime

import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}

private[server] final case class ProtocolConfiguration(handshakeTimeout: FiniteDuration,
                                                       defaultRequestTimeout: FiniteDuration,
                                                       heartbeatConfig: ProtocolConfiguration.HeartbeatConfiguration)

private[server] object ProtocolConfiguration {
  def apply(config: Config): ProtocolConfiguration = {
    val protoConfig = config.getConfig("convergence.realtime.protocol")
    ProtocolConfiguration(
      Duration.fromNanos(protoConfig.getDuration("handshake-timeout").toNanos),
      Duration.fromNanos(protoConfig.getDuration("default-request-timeout").toNanos),
      HeartbeatConfiguration(
        protoConfig.getBoolean("heartbeat.enabled"),
        Duration.fromNanos(protoConfig.getDuration("heartbeat.ping-interval").toNanos),
        Duration.fromNanos(protoConfig.getDuration("heartbeat.pong-timeout").toNanos)))
  }

  final case class HeartbeatConfiguration(enabled: Boolean,
                                          pingInterval: FiniteDuration,
                                          pongTimeout: FiniteDuration)

}