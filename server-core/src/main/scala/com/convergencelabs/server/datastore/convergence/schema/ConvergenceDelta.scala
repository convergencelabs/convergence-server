package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object ConvergenceDeltaClass extends OrientDbClass {
  val ClassName = "ConvergenceDelta"
  
  object Fields {
    val DeltaNo = "deltaNo"
    val Script = "script"
  }
  
  object Indices {
    val DeltaNo = "ConvergenceDelta.deltaNo"
  }
}
