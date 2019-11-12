/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence

case class ConnectionConfig(
  minClientPingInterval: Int,
  serverPongTimeout: Int,
  minClientPongTimeout: Int,
  handshakeTimeout: Int,
  serverPingInterval: Int,
  reconnectionTimeout: Int,
  defaultRequestTimeout: Int)
