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

package com.convergencelabs.convergence.server.backend.db.schema.cur.delta

/**
 * Represents a database delta. The delta represents a set of actions to apply
 * to a database.
 *
 * @param actions     The set of actions to apply to the database to effect
 *                    change.
 * @param description An option description describing the purpose of the
 *                    delta.
 */
private[schema] final case class Delta(actions: List[DeltaAction], description: Option[String])
