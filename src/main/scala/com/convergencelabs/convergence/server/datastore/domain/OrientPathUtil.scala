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

package com.convergencelabs.convergence.server.datastore.domain

import com.convergencelabs.convergence.server.datastore.domain.schema.DomainSchema

object OrientPathUtil {
  def toOrientPath(path: List[Any]): String = {
    val pathBuilder = new StringBuilder()
    pathBuilder.append(DomainSchema.Classes.Model.Fields.Data)
    path.foreach { p =>
      p match {
        case p: Int => pathBuilder.append(s"[$p]")
        case p: BigInt => pathBuilder.append(s"[$p]") // FIXME json4 is is giving us BigInts.
        case p: String => pathBuilder.append(s".${p}")
      }
    }
    pathBuilder.toString()
  }

  def escape(text: String): String = {
    s"`${text}`"
  }

  def appendToPath(path: String, property: String): String = {
    s"$path.${property}"
  }
}
