package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object DomainDeltaClass extends OrientDbClass {
  val ClassName = "DomainDelta"
  
  object Fields {
    val DeltaNo = "deltaNo"
    val Script = "script"
  }
  
  object Indices {
    val DeltaNo = "DomainDelta.deltaNo"
  }
}
