package com.convergencelabs.server.datastore.domain.schema

object ChatMessageEventClass extends OrientDbClass {
  val ClassName = "ChatMessageEvent"
  
  object Fields {
    val Chat = "chat"
    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "Timestamp"
    val Message = "message"
  }
}