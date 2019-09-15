package com.convergencelabs.server.domain.model

import org.json4s.JsonAST.JObject

case class ModelQueryResult(
  metaData: ModelMetaData,
  data: JObject)