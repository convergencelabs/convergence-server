/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.permissions

import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{NonGlobalPermissionTarget, PermissionsStore}
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId

import scala.util.{Failure, Success, Try}

/**
 * The PermissionsCache attempts to minimize the overhead of continually
 * resolving user permissions against a database by caching them to
 * performance.
 *
 * @param target           The target the permissions cache will calculate and
 *                         cache permissions for.
 * @param permissionsStore The underlying permissions store to use to obtain
 *                         permissions for.
 * @param validPermissions The valid permissions we can look up and cache.
 */
class PermissionsCache(target: NonGlobalPermissionTarget,
                       permissionsStore: PermissionsStore,
                       validPermissions: Set[String]) {
  private[this] var cache: Map[DomainUserId, Set[String]] = Map()

  def hasPermission(userId: Option[DomainUserId], permission: String): Try[Boolean] = {
    userId match {
      case Some(id) =>
        hasPermission(id, permission)
      case None =>
        Success(true)
    }
  }

  def hasPermission(userId: DomainUserId, permission: String): Try[Boolean] = {
    if (!validPermissions.contains(permission)) {
      Failure(new IllegalArgumentException("Can not check for a permission that was not included in the permission filter: " + permission))
    } else if (userId.isConvergence) {
      Success(true)
    } else {
      getPermissionsForUser(userId).map(_.contains(permission))
    }
  }

  /**
   * Gets permissions from the cache, reaching through to the permissions store
   * if the permissions are not cached.
   *
   * @param userId The id of the user to get permissions for.
   * @return The permissions for the target and user id.
   */
  def getPermissionsForUser(userId: DomainUserId): Try[Set[String]] = {
    if (cache.contains(userId)) {
      Success(cache(userId))
    } else {
      if (userId.isConvergence) {
        cache = cache + (userId -> this.validPermissions)
        Success(this.validPermissions)
      } else {permissionsStore.resolveUserPermissionsForTarget(userId, target, validPermissions).map { permissions =>
          cache = cache + (userId -> permissions)
          permissions
        }
      }
    }
  }

  /**
   * Clears the permissions for a specific user.
   *
   * @param userId The id of the user to clear permissions for.
   */
  def invalidateUser(userId: DomainUserId): Unit = {
    cache = cache - userId
  }

  /**
   * Clears all permissions in the cache.
   */
  def invalidate(): Unit = {
    cache = Map()
  }
}
