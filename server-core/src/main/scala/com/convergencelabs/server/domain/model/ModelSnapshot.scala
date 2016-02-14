package com.convergencelabs.server.domain.model

import org.json4s.JsonAST.JValue

case class ModelSnapshot(
  metaData: ModelSnapshotMetaData,
  data: JValue)
