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
