/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import java.time.Instant

case class ModelSnapshotMetaData(
  modelId: String,
  version: Long,
  timestamp: Instant)
