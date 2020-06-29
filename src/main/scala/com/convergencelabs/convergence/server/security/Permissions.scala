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

/**
 * Constants for server permissions.
 */
object Permissions {

  /**
   * Permissions that apply at the server level.
   */
  object Server {
    /**
     * Required to log in to the server.
     */
    val Access = "access"

    /**
     * Allows a user to manage all domains / namespaces
     * in the server.
     */
    val ManageDomains = "manage-domains"

    /**
     * Allows the user to manage the settings of the server.
     */
    val ManageSettings = "manage-settings"

    /**
     * Allows the user to manage the server users.
     */
    val ManageUsers= "manage-users"
  }

  /**
   * Permissions that apply to Namespaces.
   */
  object Namespace {
    /**
     * Allows the user to access the domains in the namespace.
     */
    val Access = "namespace-access"

    /**
     * Allows the user to create domains in the namespace.
     */
    val ManageDomains = "namespace-manage-domains"

    /**
     * Allows the user to manage who has access to the namespace.
     */
    val ManageUsers= "namespace-manage-users"
  }

  /**
   * Permissions that apply to Domains.
   */
  object Domain {
    /**
     * Allows the user to use the domain.
     */
    val Access = "domain-access"

    /**
     * Allows the user to manage the setting of the domain.
     */
    val ManageSettings = "domain-manage-settings"

    /**
     * Allows the user to manage the domain members.
     */
    val ManageUsers= "domain-manage-users"
  }
}
