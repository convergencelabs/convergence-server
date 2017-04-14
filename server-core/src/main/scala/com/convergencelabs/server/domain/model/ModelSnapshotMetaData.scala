package com.convergencelabs.server.domain.model

import java.time.Instant

case class ModelSnapshotMetaData(
  modelId: String,
  version: Long,
  timestamp: Instant)
