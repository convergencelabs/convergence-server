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

import java.time.LocalDate

import com.fasterxml.jackson.annotation.JsonFormat

/**
 * Provides meta data information on a specific software version.
 *
 * @param released     Whether this version is released or not.
 * @param releaseDate  The date this version was released, may not be set if
 *                     the version hasn't been released yet.
 * @param schemaSha256 The sha256 hash of the schema file, if released
 * @param deltas       The deltas that this version incorporates.
 */
private[schema] final case class SchemaVersionManifest(released: Boolean,
                                                       @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd")
                                                       releaseDate: Option[LocalDate],
                                                       schemaSha256: Option[String],
                                                       deltas: List[DeltaEntry])
