package com.convergencelabs.server.domain.model

import java.time.Instant

case class OpenModelMetaData(
  version: Long,
  createdTime: Instant,
  modifiedTime: Instant)
