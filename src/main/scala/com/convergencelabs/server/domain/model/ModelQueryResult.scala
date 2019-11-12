/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import org.json4s.JsonAST.JObject

case class ModelQueryResult(
  metaData: ModelMetaData,
  data: JObject)