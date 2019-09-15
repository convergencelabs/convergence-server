package com.convergencelabs.server.datastore.domain.schema

object ChatCreatedEventClass extends OrientDbClass {
  val ClassName = "ChatCreatedEvent"
  
  object Fields {
    val Chat = "chat"
    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "timestamp"
    
    val Name = "name"
    val Topic = "topic"
    val Members = "members"
  }
}