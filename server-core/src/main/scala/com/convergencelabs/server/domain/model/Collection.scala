package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.datastore.domain.CollectionPermissions

case class Collection(
  id: String,
  name: String,
  overrideSnapshotConfig: Boolean,
  snapshotConfig: ModelSnapshotConfig,
  worldPermissions: CollectionPermissions
)
