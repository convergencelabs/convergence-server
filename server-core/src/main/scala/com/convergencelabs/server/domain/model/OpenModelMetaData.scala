package com.convergencelabs.server.domain.model

import java.time.Instant

case class OpenModelMetaData(
  id: String,
  collection: String,
  version: Int,
  createdTime: Instant,
  modifiedTime: Instant)
