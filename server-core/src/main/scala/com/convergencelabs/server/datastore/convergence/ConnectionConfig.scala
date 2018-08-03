package com.convergencelabs.server.datastore.convergence

case class ConnectionConfig(
  minClientPingInterval: Int,
  serverPongTimeout: Int,
  minClientPongTimeout: Int,
  handshakeTimeout: Int,
  serverPingInterval: Int,
  reconnectionTimeout: Int,
  defaultRequestTimeout: Int)
