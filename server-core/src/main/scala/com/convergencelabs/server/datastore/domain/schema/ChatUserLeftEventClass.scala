package com.convergencelabs.server.datastore.domain.schema

object ChatUserLeftEventClass extends OrientDbClass {
  val ClassName = "ChatUserLeftEvent"
  
  object Fields {
    val Chat = "chat"
    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "Timestamp"
  }
}