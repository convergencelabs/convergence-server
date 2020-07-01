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

package com.convergencelabs.convergence.server.backend.datastore.convergence.schema

object ConvergenceSchema {
  object Classes {
    val Config: ConfigClass.type = ConfigClass
    val ConvergenceDelta: ConvergenceDeltaClass.type = ConvergenceDeltaClass
    val ConvergenceDeltaHistory: ConvergenceDeltaHistoryClass.type = ConvergenceDeltaHistoryClass
    val Domain: DomainClass.type = DomainClass
    val DomainDelta: DomainDeltaClass.type = DomainDeltaClass
    val DomainDeltaHistory: DomainDeltaHistoryClass.type = DomainDeltaHistoryClass
    val Namespace: NamespaceClass.type = NamespaceClass
    val Permission: PermissionClass.type = PermissionClass
    val Role: RoleClass.type = RoleClass
    val User: UserClass.type = UserClass
    val UserApiKey: UserApiKeyClass.type = UserApiKeyClass
    val UserRole: UserRoleClass.type = UserRoleClass
    val UserSessionToken: UserSessionTokenClass.type = UserSessionTokenClass
  }

  object Sequences {

  }
}
