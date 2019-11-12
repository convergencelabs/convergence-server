/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import java.time.Instant

import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.model.ot.AppliedOperation

case class ModelOperation(
  modelId: String,
  version: Long,
  timestamp: Instant,
  userId: DomainUserId,
  sessionId: String,
  op: AppliedOperation)

  
case class NewModelOperation(
  modelId: String,
  version: Long,
  timestamp: Instant,
  sessionId: String,
  op: AppliedOperation)