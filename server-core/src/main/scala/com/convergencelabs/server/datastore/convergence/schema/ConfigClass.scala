package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object ConfigClass extends OrientDbClass {
  val ClassName = "Config"
  
  object Fields {
    val Key = "key"
    val Value = "value"
  }
  
  object Indices {
    val Key = "Config.key"
  }
}
