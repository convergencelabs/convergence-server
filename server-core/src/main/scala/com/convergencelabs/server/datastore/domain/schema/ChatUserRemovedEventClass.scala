package com.convergencelabs.server.datastore.domain.schema

object ChatUserRemovedEventClass extends OrientDbClass {
  val ClassName = "ChatUserRemovedEvent"
  
  object Fields {
    val Chat = "chat"
    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "Timestamp"
  }
}