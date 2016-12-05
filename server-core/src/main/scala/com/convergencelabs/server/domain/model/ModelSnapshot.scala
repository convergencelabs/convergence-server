package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.model.data.ObjectValue

case class ModelSnapshot(
  metaData: ModelSnapshotMetaData,
  data: ObjectValue)
