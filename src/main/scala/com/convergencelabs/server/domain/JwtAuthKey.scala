/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain

import java.time.Instant

case class JwtAuthKey(
  id: String,
  description: String,
  updated: Instant,
  key: String,
  enabled: Boolean)
