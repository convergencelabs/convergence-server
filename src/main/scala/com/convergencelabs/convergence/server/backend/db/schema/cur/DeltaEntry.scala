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

package com.convergencelabs.convergence.server.backend.db.schema.cur

/**
 * Represents a Delta listed within a [[SchemaVersionManifest]].
 *
 * @param deltaId     The id of the delta.
 * @param sha256      The optional sha256 to check the delta against.
 * @param backport    If this was a backported delta.
 * @param backportTag If this entry is a backport, and if that backport
 *                    has a different version of the delta, identified
 *                    by a suffix on the filename. Generally, the suffix
 *                    will be the version(s) of the database that the delta
 *                    applies to.
 */
private[schema] final case class DeltaEntry(deltaId: String,
                                            sha256: Option[String],
                                            backport: Option[Boolean],
                                            backportTag: Option[String])
