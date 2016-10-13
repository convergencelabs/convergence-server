package com.convergencelabs.server.domain.model

import java.time.Instant
import com.convergencelabs.server.domain.model.ot.AppliedOperation

case class ModelOperation(
  modelFqn: ModelFqn,
  version: Long,
  timestamp: Instant,
  username: String,
  sid: String,
  op: AppliedOperation)
