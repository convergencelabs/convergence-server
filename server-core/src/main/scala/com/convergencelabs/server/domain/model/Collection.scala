package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.ModelSnapshotConfig

case class Collection(
  id: String,
  name: String,
  overrideSnapshotConfig: Boolean,
  snapshotConfig: ModelSnapshotConfig
)
