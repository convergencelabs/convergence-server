package com.convergencelabs.server.domain.model

import java.time.Instant

case class OpenModelMetaData(
  id: String,
  collection: String,
  version: Long,
  createdTime: Instant,
  modifiedTime: Instant)
