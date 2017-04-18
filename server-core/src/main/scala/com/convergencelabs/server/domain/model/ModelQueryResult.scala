package com.convergencelabs.server.domain.model

import org.json4s.JsonAST.JValue

case class ModelQueryResult(
  meta: ModelMetaData,
  data: JValue)