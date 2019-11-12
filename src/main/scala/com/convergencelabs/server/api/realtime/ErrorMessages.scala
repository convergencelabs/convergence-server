/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.api.realtime

import com.convergencelabs.convergence.proto.core._

object ErrorMessages {
  def Unauthorized(message: String): ErrorMessage = {
    ErrorMessage("unauthorized", message, Map())
  }
}