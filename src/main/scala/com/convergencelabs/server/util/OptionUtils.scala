/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.util

object OptionUtils {
  def toNullable[T >: Null](option: Option[T]): T = {
    option match {
      case Some(value) => value
      case None => null
    }
  }
}
