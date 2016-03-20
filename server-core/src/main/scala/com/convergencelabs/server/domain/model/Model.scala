package com.convergencelabs.server.domain.model

import org.json4s.JsonAST.JValue

// FIXME remove JValues
case class Model(
  metaData: ModelMetaData,
  data: JValue)
