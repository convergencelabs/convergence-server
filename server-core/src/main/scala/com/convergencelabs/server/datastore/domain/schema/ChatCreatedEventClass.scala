package com.convergencelabs.server.datastore.domain.schema

object ChatCreatedEventClass extends OrientDbClass {
  val ClassName = "ChatCreatedEvent"
  
  object Fields {
    val Chat = "chat"
    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "Timestamp"
    
    val Name = "name"
    val Topic = "topic"
    val Members = "Members"
  }
}