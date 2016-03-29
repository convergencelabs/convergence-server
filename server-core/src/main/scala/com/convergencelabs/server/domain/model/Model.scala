package com.convergencelabs.server.domain.model

import com.convergencelabs.server.domain.model.data.ObjectValue

case class Model(
  metaData: ModelMetaData,
  data: ObjectValue)
