/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.datastore.domain.CollectionPermissions

case class Collection(
  id: String,
  description: String,
  overrideSnapshotConfig: Boolean,
  snapshotConfig: ModelSnapshotConfig,
  worldPermissions: CollectionPermissions
)
