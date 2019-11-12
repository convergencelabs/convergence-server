/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain

import java.time.Duration

case class ModelSnapshotConfig(
  snapshotsEnabled: Boolean,
  triggerByVersion: Boolean,
  limitedByVersion: Boolean,
  minimumVersionInterval: Long,
  maximumVersionInterval: Long,
  triggerByTime: Boolean,
  limitedByTime: Boolean,
  minimumTimeInterval: Duration,
  maximumTimeInterval: Duration)
