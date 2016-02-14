package com.convergencelabs.server.domain.model

import java.time.Instant

case class ModelMetaData(
  fqn: ModelFqn,
  version: Long,
  createdTime: Instant,
  modifiedTime: Instant)
