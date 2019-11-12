/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.datastore.domain.schema.DomainSchema

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
