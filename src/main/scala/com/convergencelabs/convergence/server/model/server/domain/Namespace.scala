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

package com.convergencelabs.convergence.server.model.server.domain

/**
 * The [[Namespace]] class holds the meta data for a single namespace in the
 * system. Namespaces group Domains. Namespaces can be "user namespaces"
 * which means they are a private space where Convergence users can create
 * domains without needing explicit permissions to the namespace.
 *
 * @param id            The unique id of the namespace.
 * @param displayName   A more friendly display name.
 * @param userNamespace True if the namespace is a user namespace, false
 *                      .otherwise
 */
final case class Namespace(id: String, displayName: String, userNamespace: Boolean)
