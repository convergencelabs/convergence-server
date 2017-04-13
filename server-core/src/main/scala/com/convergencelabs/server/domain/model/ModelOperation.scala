package com.convergencelabs.server.domain.model

import java.time.Instant
import com.convergencelabs.server.domain.model.ot.AppliedOperation

case class ModelOperation(
  modelId: String,
  version: Long,
  timestamp: Instant,
  username: String,
  sessionId: String,
  op: AppliedOperation)

  
case class NewModelOperation(
  modelId: String,
  version: Long,
  timestamp: Instant,
  sessionId: String,
  op: AppliedOperation)