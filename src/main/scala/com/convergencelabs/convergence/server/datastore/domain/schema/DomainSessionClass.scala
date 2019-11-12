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

package com.convergencelabs.convergence.server.datastore.domain.schema

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
