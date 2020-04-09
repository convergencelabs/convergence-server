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

package com.convergencelabs.convergence.server.security

object Permissions {
  object Global {
    val Access = "access"
    val ManageDomains = "manage-domains"
    val ManageSettings = "manage-settings"
    val ManageUsers= "manage-users"
  }
  
  object Namespace {
    val Access = "namespace-access"
    val ManageDomains = "namespace-manage-domains"
    val ManageUsers= "namespace-manage-users"
  }
  
  object Domain {
    val Access = "domain-access"
    val ManageSettings = "domain-manage-settings"
    val ManageUsers= "domain-manage-users"
  }
}
