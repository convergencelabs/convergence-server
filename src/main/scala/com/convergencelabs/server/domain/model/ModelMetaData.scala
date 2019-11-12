/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import java.time.Instant
import com.convergencelabs.server.datastore.domain.ModelPermissions

case class ModelMetaData(
  id: String,
  collection: String,
  version: Long,
  createdTime: Instant,
  modifiedTime: Instant,
  overridePermissions: Boolean,
  worldPermissions: ModelPermissions,
  valuePrefix: Long)
