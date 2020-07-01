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

package com.convergencelabs.convergence.server.backend.datastore.domain.permissions

import com.orientechnologies.orient.core.id.ORID

/**
 * The [[PermissionGrantee]] trait is an ADT that defines the who / what a
 * permission has been granted to. It is used internally to generate
 * queries that match certain permission grants.
 */
private[permissions] sealed trait PermissionGrantee

private[permissions] case object AnyGrantee extends PermissionGrantee

private[permissions] case object GrantedToWorld extends PermissionGrantee

private[permissions] case object GrantedToAnyUser extends PermissionGrantee

private[permissions] case object GrantedToAnyGroup extends PermissionGrantee

private[permissions] final case class GrantedToRid(rid: ORID) extends PermissionGrantee
