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

import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

/**
 * Represents the options an OrientDB field can have.
 */
private[schema] final case class PropertyOptions(name: Option[String],
                                                 @JsonScalaEnumeration(classOf[OrientTypeTypeReference])
                                                 orientType: Option[OrientType.Value],
                                                 @JsonScalaEnumeration(classOf[OrientTypeTypeReference])
                                                 linkedType: Option[OrientType.Value],
                                                 linkedClass: Option[String],
                                                 constraints: Option[PropertyConstraints])
