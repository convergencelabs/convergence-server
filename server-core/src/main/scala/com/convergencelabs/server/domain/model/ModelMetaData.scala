package com.convergencelabs.server.domain.model

import java.time.Instant
import com.convergencelabs.server.datastore.domain.ModelPermissions

case class ModelMetaData(
  collectionId: String,
  modelId: String,
  version: Int,
  createdTime: Instant,
  modifiedTime: Instant,
  overridePermissions: Boolean,
  worldPermissions: ModelPermissions,
  valuePrefix: Long)
