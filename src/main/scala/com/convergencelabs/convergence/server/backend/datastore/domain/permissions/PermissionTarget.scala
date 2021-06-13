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

import com.convergencelabs.convergence.server.model.domain.activity.ActivityId

/**
 * Defines the target that a permission applies to.
 */
sealed trait PermissionTarget

/**
 * Specifies a global permission that is not scoped to a particular entity
 * in the system.
 */
case object GlobalPermissionTarget extends PermissionTarget

/**
 * A super trait for any permission target that is not global.
 */
sealed trait NonGlobalPermissionTarget extends PermissionTarget

/**
 * Specifies a Chat as the target of a permission.
 *
 * @param chatId The id of the chat that is the target of the permission
 */
final case class ChatPermissionTarget(chatId: String) extends NonGlobalPermissionTarget

/**
 * Specifies an Activity as the target of a permission.
 *
 * @param id The id of the activity that is the target of the permission
 */
final case class ActivityPermissionTarget(activityId: ActivityId) extends NonGlobalPermissionTarget