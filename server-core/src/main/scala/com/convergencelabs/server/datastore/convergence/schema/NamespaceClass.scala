package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object NamespaceClass extends OrientDbClass {
  val ClassName = "Namespace"
  
  object Fields {
    val Id = "id"
    val DisplayName = "displayName"
  }
  
  object Indices {
    val Id = "Namespace.id"
    val DisplayName = "Namespace.displayName"
  }
}
