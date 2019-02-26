package com.convergencelabs.server.datastore.domain.schema

object ChatNameChangedEventClass extends OrientDbClass {
  val ClassName = "ChatNameChangedEvent"
  
  object Fields {
    val Chat = "chat"
    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "timestamp"
    
    val Name = "name"
  }
}