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

package com.convergencelabs.convergence.server.backend.db.schema.delta

/**
 * The constraints a property can have.
 */
private[schema] final case class PropertyConstraints(min: Option[String],
                                                     max: Option[String],
                                                     mandatory: Option[Boolean],
                                                     readOnly: Option[Boolean],
                                                     notNull: Option[Boolean],
                                                     regex: Option[String],
                                                     collate: Option[String],
                                                     custom: Option[CustomProperty],
                                                     default: Option[String])
