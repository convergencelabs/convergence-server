/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object DomainSessionClass extends OrientDbClass {
  val ClassName = "DomainSession"

  object Indices {
    val Id = "DomainSession.id"
  }

  object Fields {
    val Id = "id"
    val User = "user"
    val Connected = "connected"
    val Disconnected = "disconnected"
    val AuthMethod = "authMethod"
    val Client = "client"
    val ClientVersion = "clientVersion"
    val ClientMetaData = "clientMetaData"
    val RemoteHost = "remoteHost"
  }
}
