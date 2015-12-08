package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.DomainFqn

case class ConnectionConfig(
  minClientPingInterval: Int,
  serverPongTimeout: Int,
  minClientPongTimeout: Int,
  handshakeTimeout: Int,
  serverPingInterval: Int,
  reconnectionTimeout: Int,
  defaultRequestTimeout: Int)

case class RestConfig(tokenDuration: Int, maxTokenDuration: Int)
