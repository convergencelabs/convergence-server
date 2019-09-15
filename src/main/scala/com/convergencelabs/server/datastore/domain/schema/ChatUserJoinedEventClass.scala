package com.convergencelabs.server.datastore.domain.schema

object ChatUserJoinedEventClass extends OrientDbClass {
  val ClassName = "ChatUserJoinedEvent"
  
  object Fields {
    val Chat = "chat"
    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "timestamp"
  }
}