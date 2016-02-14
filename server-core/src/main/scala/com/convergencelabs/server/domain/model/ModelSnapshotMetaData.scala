package com.convergencelabs.server.domain.model

import java.time.Instant

case class ModelSnapshotMetaData(
  fqn: ModelFqn,
  version: Long,
  timestamp: Instant)
