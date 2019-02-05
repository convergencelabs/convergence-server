package com.convergencelabs.server.datastore.convergence.schema

import com.convergencelabs.server.datastore.domain.schema.OrientDbClass

object DomainDeltaHistoryClass extends OrientDbClass {
  val ClassName = "DomainDeltaHistory"
  
  object Fields {
    val Domain = "domain"
    val Delta = "delta"
    val Status = "status"
    val Message = "message"
    val Date = "date"
  }
  
  object Indices {
    val DomainDelta = "DomainDeltaHistory.domain_delta"
  }
}
