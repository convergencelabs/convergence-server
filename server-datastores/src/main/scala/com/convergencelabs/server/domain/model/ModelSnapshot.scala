package com.convergencelabs.server.domain.model

import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.data.ObjectValue

case class ModelSnapshot(
  metaData: ModelSnapshotMetaData,
  data: ObjectValue)
