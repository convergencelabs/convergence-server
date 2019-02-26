package com.convergencelabs.server.datastore.domain.schema

object ChatUserAddedEventClass extends OrientDbClass {
  val ClassName = "ChatUserAddedEvent"
  
  object Fields {
    val Chat = "chat"
    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "Timestamp"
  }
}