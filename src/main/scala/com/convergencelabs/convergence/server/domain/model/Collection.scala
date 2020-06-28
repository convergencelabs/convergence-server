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

package com.convergencelabs.convergence.server.domain.model

import com.convergencelabs.convergence.server.datastore.domain.CollectionPermissions
import com.convergencelabs.convergence.server.domain.ModelSnapshotConfig

/**
 * Represents a RealtimeModel collection.
 *
 * @param id                     The unique id of the collection.
 * @param description            The description of the collection.
 * @param overrideSnapshotConfig Determines if the collection overrides the
 *                               default server snapshot configuration.
 * @param snapshotConfig         The snapshot config for this collection. It
 *                               will only be used of the collection is set
 *                               to override the default.
 * @param worldPermissions       The permissions everyone has for the
 *                               collection unless they have specifically
 *                               set permissions.
 */
final case class Collection(id: String,
                            description: String,
                            overrideSnapshotConfig: Boolean,
                            snapshotConfig: ModelSnapshotConfig,
                            worldPermissions: CollectionPermissions)
